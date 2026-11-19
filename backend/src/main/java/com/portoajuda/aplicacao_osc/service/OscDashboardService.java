package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** NGO-scoped management metrics. Candidate PII and Stripe balances are never included. */
@Service
public class OscDashboardService {
    private final JdbcTemplate jdbc;
    private final OscRepository oscs;
    private final OscService oscService;

    public OscDashboardService(JdbcTemplate jdbc, OscRepository oscs, OscService oscService) {
        this.jdbc = jdbc; this.oscs = oscs; this.oscService = oscService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(Integer oscId, Usuario actor) {
        return dashboard(oscId, actor, null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(Integer oscId, Usuario actor, LocalDate from, LocalDate to) {
        if (oscId == null || oscId < 1 || actor == null || actor.getId() == null) throw forbidden();
        Osc osc = oscs.findById(oscId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organização não encontrada."));
        CargoOsc cargo = oscService.obterCargoUsuario(osc, actor);
        if (osc.isArquivada() || (cargo != CargoOsc.CRIADOR && cargo != CargoOsc.ADMINISTRADOR)) throw forbidden();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", Map.of("oscId", oscId, "ngoName", osc.getNome(), "period", "Desde o início dos registros disponíveis"));
        result.put("membersByRole", group("SELECT cargo_osc AS label, count(*) AS total FROM osc_membros WHERE id_osc=? GROUP BY cargo_osc ORDER BY cargo_osc", oscId));
        result.put("publications", one("""
                SELECT count(*) FILTER (WHERE tipo<>'VOLUNTARIADO' AND ativo) AS published,
                       count(*) FILTER (WHERE tipo<>'VOLUNTARIADO' AND NOT ativo) AS unpublished,
                       count(*) FILTER (WHERE tipo='VOLUNTARIADO' AND ativo) AS volunteerPosts,
                       count(*) FILTER (WHERE tipo='VOLUNTARIADO' AND NOT ativo) AS unpublishedVolunteerPosts
                  FROM post WHERE id_osc=?
                """, oscId));
        result.put("opportunities", one("""
                SELECT count(*) FILTER (WHERE v.status='ABERTA' AND v.data_fim>=CURRENT_DATE
                            AND p.ativo AND v.quantidade_vagas > COALESCE(a.occupied,0)) AS open,
                       count(*) FILTER (WHERE v.status='ABERTA' AND v.data_fim>=CURRENT_DATE
                            AND p.ativo AND v.quantidade_vagas > 0 AND v.quantidade_vagas <= COALESCE(a.occupied,0)) AS full,
                       count(*) FILTER (WHERE v.data_conclusao IS NOT NULL) AS completed,
                       count(*) FILTER (WHERE v.status='CANCELADA') AS canceled,
                       COALESCE(sum(v.quantidade_vagas) FILTER (WHERE v.status='ABERTA' AND v.data_fim>=CURRENT_DATE AND p.ativo),0) AS totalCapacity,
                       COALESCE(sum(LEAST(v.quantidade_vagas,COALESCE(a.occupied,0))) FILTER (WHERE v.status='ABERTA' AND v.data_fim>=CURRENT_DATE AND p.ativo),0) AS occupiedSlots,
                       COALESCE(sum(GREATEST(v.quantidade_vagas-COALESCE(a.occupied,0),0)) FILTER (WHERE v.status='ABERTA' AND v.data_fim>=CURRENT_DATE AND p.ativo),0) AS availableSlots
                  FROM vagas_voluntariado v JOIN post p ON p.id=v.id_post
                  LEFT JOIN (SELECT id_vaga,count(*) AS occupied FROM candidaturas_voluntariado WHERE status='APROVADA' GROUP BY id_vaga) a ON a.id_vaga=v.id
                 WHERE p.id_osc=?
                """, oscId));
        result.put("applications", one("""
                SELECT count(*) FILTER (WHERE c.status='PENDENTE') AS pending,
                       count(*) FILTER (WHERE c.status='APROVADA') AS accepted,
                       count(*) FILTER (WHERE c.status='RECUSADA') AS rejected,
                       count(*) FILTER (WHERE c.status='DESISTIU') AS canceled,
                       count(*) FILTER (WHERE c.status='CONCLUIDA') AS completed,
                       count(*) FILTER (WHERE c.status='CANCELADA_PELA_ONG') AS canceledByNgo
                  FROM candidaturas_voluntariado c JOIN vagas_voluntariado v ON v.id=c.id_vaga
                  JOIN post p ON p.id=v.id_post WHERE p.id_osc=?
                """, oscId));
        result.put("campaigns", campaigns(oscId));
        Map<String,Object> pendingActions = new LinkedHashMap<>(one("""
                SELECT count(*) AS pendingApplications
                  FROM candidaturas_voluntariado c JOIN vagas_voluntariado v ON v.id=c.id_vaga
                  JOIN post p ON p.id=v.id_post WHERE p.id_osc=? AND c.status='PENDENTE'
                """, oscId));
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.donation_campaigns') IS NOT NULL", Boolean.class))) {
            Long drafts = jdbc.queryForObject("SELECT count(*) FROM donation_campaigns WHERE osc_id=? AND state='DRAFT'", Long.class, oscId);
            pendingActions.put("draftCampaigns", drafts == null ? 0L : drafts);
        }
        result.put("pendingActions", pendingActions);
        result.put("stripeBalance", Map.of("available", false, "label", "Indisponível"));
        result.put("recentActivity", recent(oscId));
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 36525) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um período válido de até cem anos.");
        }
        result.put("period", Map.of("from", start.toString(), "to", end.toString(), "semantics", "Data de criação das candidaturas e data dos lançamentos financeiros; status de candidaturas mostra o estado atual."));
        result.put("fundraisingEvolution", campaignSeries(oscId, start, end));
        result.put("applicationsOverTime", jdbc.queryForList("""
                SELECT date_trunc('month', c.data_candidatura)::date AS period, count(*) AS total
                  FROM candidaturas_voluntariado c JOIN vagas_voluntariado v ON v.id=c.id_vaga
                  JOIN post p ON p.id=v.id_post WHERE p.id_osc=? AND c.data_candidatura BETWEEN ? AND ?
                 GROUP BY 1 ORDER BY 1
                """, oscId, start, end));
        result.put("applicationStatusDistribution", jdbc.queryForList("""
                SELECT c.status AS label, count(*) AS total FROM candidaturas_voluntariado c
                  JOIN vagas_voluntariado v ON v.id=c.id_vaga JOIN post p ON p.id=v.id_post
                 WHERE p.id_osc=? AND c.data_candidatura BETWEEN ? AND ? GROUP BY c.status ORDER BY c.status
                """, oscId, start, end));
        result.put("campaignComparison", campaignComparison(oscId));
        return result;
    }

    private List<Map<String,Object>> campaignSeries(Integer oscId, LocalDate from, LocalDate to) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.donation_campaigns') IS NOT NULL", Boolean.class))) return List.of();
        return jdbc.queryForList("""
                SELECT date_trunc('month',l.created_at)::date AS period, COALESCE(sum(l.net_delta_cents),0) AS netCents
                  FROM donation_campaign_ledger_entries l JOIN donation_campaign_contributions d ON d.id=l.contribution_id
                  JOIN donation_campaigns c ON c.id=d.campaign_id WHERE c.osc_id=? AND l.created_at >= ? AND l.created_at < (? + INTERVAL '1 day')
                 GROUP BY 1 ORDER BY 1
                """, oscId, from, to);
    }

    private List<Map<String,Object>> campaignComparison(Integer oscId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.donation_campaigns') IS NOT NULL", Boolean.class))) return List.of();
        return jdbc.queryForList("""
                SELECT c.id, c.title, c.goal_net_cents AS goalNetCents,
                       COALESCE(sum(l.net_delta_cents),0) AS confirmedNetCents
                  FROM donation_campaigns c LEFT JOIN donation_campaign_contributions d ON d.campaign_id=c.id
                  LEFT JOIN donation_campaign_ledger_entries l ON l.contribution_id=d.id
                 WHERE c.osc_id=? GROUP BY c.id,c.title,c.goal_net_cents ORDER BY c.created_at DESC LIMIT 12
                """, oscId);
    }

    private Map<String, Object> campaigns(Integer oscId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.donation_campaigns') IS NOT NULL", Boolean.class))) {
            return Map.of("available", false, "label", "Indisponível");
        }
        Map<String, Object> states = new LinkedHashMap<>();
        jdbc.query("SELECT state,count(*) AS total,COALESCE(sum(goal_net_cents),0) AS goals FROM donation_campaigns WHERE osc_id=? GROUP BY state", rs -> {
            states.put(rs.getString("state"), Map.of("count",rs.getLong("total"),"goalNetCents",rs.getLong("goals")));
        }, oscId);
        Long raised = jdbc.queryForObject("""
                SELECT COALESCE(sum(l.net_delta_cents),0)
                  FROM donation_campaigns c
                  LEFT JOIN donation_campaign_contributions d ON d.campaign_id=c.id
                  LEFT JOIN donation_campaign_ledger_entries l ON l.contribution_id=d.id
                 WHERE c.osc_id=?
                """, Long.class, oscId);
        return Map.of("available", true, "states", states, "goalNetCents", jdbc.queryForObject(
                "SELECT COALESCE(sum(goal_net_cents),0) FROM donation_campaigns WHERE osc_id=?", Long.class, oscId),
                "confirmedNetRaisedCents", raised == null ? 0L : raised,
                "raisedLabel", "Arrecadado líquido confirmado (histórico)",
                "balanceLabel", "Saldo disponível para saque: Indisponível");
    }

    private List<Map<String, Object>> recent(Integer oscId) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.donation_campaigns') IS NOT NULL", Boolean.class))) {
            return jdbc.queryForList("""
                    SELECT kind,title,occurred_at AS "occurredAt" FROM (
                        SELECT CASE WHEN p.tipo='VOLUNTARIADO' THEN 'Voluntariado' ELSE 'Publicação' END AS kind,
                               p.titulo AS title,p.data_atualizacao AS occurred_at
                          FROM post p WHERE p.id_osc=?
                        UNION ALL
                        SELECT 'Vaquinha' AS kind,c.title AS title,c.updated_at AS occurred_at
                          FROM donation_campaigns c WHERE c.osc_id=?
                    ) activity ORDER BY occurred_at DESC NULLS LAST LIMIT 8
                    """, oscId, oscId);
        }
        return jdbc.queryForList("SELECT CASE WHEN tipo='VOLUNTARIADO' THEN 'Voluntariado' ELSE 'Publicação' END AS kind,titulo AS title,data_atualizacao AS \"occurredAt\" FROM post WHERE id_osc=? ORDER BY data_atualizacao DESC NULLS LAST LIMIT 8", oscId);
    }

    private List<Map<String, Object>> group(String sql, Integer oscId) { return jdbc.queryForList(sql, oscId); }
    private Map<String, Object> one(String sql, Integer oscId) { return jdbc.queryForMap(sql, oscId); }
    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente CRIADOR e ADMINISTRADOR desta ONG podem acessar o dashboard.");
    }
}
