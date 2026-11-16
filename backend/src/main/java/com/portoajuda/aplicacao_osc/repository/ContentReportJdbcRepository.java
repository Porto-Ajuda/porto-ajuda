package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.dto.response.AdminContentReportDetailDTO;
import com.portoajuda.aplicacao_osc.dto.response.AdminContentReportQueueItemDTO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContentReportJdbcRepository {
    private final JdbcTemplate jdbc;

    public ContentReportJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean blocked(String type, int id) {
        Boolean value = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public.content_moderation_blocks
                 WHERE target_type = ? AND target_id = ? AND active)
                """, Boolean.class, type, id);
        return Boolean.TRUE.equals(value);
    }

    public boolean insert(String type, int id, String contentKind, String reporterKey, String reason, String details) {
        return jdbc.update("""
                INSERT INTO public.content_reports
                  (target_type, target_id, content_kind, reporter_key, reason, details)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (target_type, target_id, reporter_key) WHERE status = 'PENDING' DO NOTHING
                """, type, id, contentKind, reporterKey, reason, details) == 1;
    }

    public boolean hasPendingReport(String type, int id, String reporterKey) {
        Boolean pending = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM public.content_reports
                     WHERE target_type=? AND target_id=? AND reporter_key=? AND status='PENDING'
                )
                """, Boolean.class, type, id, reporterKey);
        return Boolean.TRUE.equals(pending);
    }

    public boolean hasPendingReports(String type, int id) {
        Boolean pending = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM public.content_reports
                              WHERE target_type=? AND target_id=? AND status='PENDING')
                """, Boolean.class, type, id);
        return Boolean.TRUE.equals(pending);
    }

    public String pendingContentKind(String type, int id) {
        List<String> kinds = jdbc.query("""
                SELECT DISTINCT content_kind FROM public.content_reports
                 WHERE target_type=? AND target_id=? AND status='PENDING'
                 ORDER BY content_kind
                """, (rs, row) -> rs.getString("content_kind"), type, id);
        return kinds.size() == 1 ? kinds.getFirst() : null;
    }

    public Instant latestReviewedAt(String type, int id, String reporterKey) {
        List<Instant> values = jdbc.query("""
                SELECT reviewed_at FROM public.content_reports
                 WHERE target_type=? AND target_id=? AND reporter_key=?
                   AND status IN ('DISMISSED','UPHELD') AND reviewed_at IS NOT NULL
                 ORDER BY reviewed_at DESC, id DESC
                 LIMIT 1
                """, (rs, row) -> instant(rs.getTimestamp("reviewed_at")), type, id, reporterKey);
        return values.isEmpty() ? null : values.getFirst();
    }

    public long qualifyingCount(String type, int id, String contentKind, int hours) {
        Long count = jdbc.queryForObject("""
                SELECT count(DISTINCT reporter_key) FROM public.content_reports
                 WHERE target_type = ? AND target_id = ? AND status = 'PENDING'
                   AND content_kind = ?
                   AND created_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 hour')
                """, Long.class, type, id, contentKind, hours);
        return count == null ? 0 : count;
    }

    public int activateAutomaticProtection(String type, int id, String contentKind) {
        return jdbc.update("""
                INSERT INTO public.content_moderation_blocks
                  (target_type, target_id, content_kind, source, reason)
                VALUES (?, ?, ?, 'AUTOMATIC_PROTECTION', 'Ocultacao preventiva por volume de denuncias pendentes.')
                ON CONFLICT (target_type, target_id, source) WHERE active DO NOTHING
                """, type, id, contentKind);
    }

    public long pendingCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM public.content_reports WHERE status = 'PENDING'", Long.class);
        return count == null ? 0 : count;
    }

    public boolean hasReportHistory(String type, int id) {
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public.content_reports WHERE target_type=? AND target_id=?)
                """, Boolean.class, type, id);
        return Boolean.TRUE.equals(found);
    }

    public List<AdminContentReportQueueItemDTO> queue(String type, String state,
            int oscHours, int publicationHours, int volunteerHours, int limit, int offset) {
        if ("ACTIVE".equals(state)) return queueActive(type, oscHours, publicationHours, volunteerHours, limit, offset);
        if ("RESOLVED".equals(state)) return queueResolved(type, limit, offset);
        String typeFilter = type == null ? "" : " AND r.target_type = ? ";
        String sql = """
                SELECT r.target_type, r.target_id,
                       coalesce(o.nome, p.titulo, '[conteudo indisponivel]') AS titulo,
                       coalesce(o.nome, p.titulo, '[conteudo indisponivel]') AS responsavel,
                       count(*) AS pendentes,
                       count(*) FILTER (WHERE r.created_at >= CURRENT_TIMESTAMP -
                         ((CASE WHEN r.target_type='OSC' THEN ? WHEN r.content_kind='VOLUNTEERING' THEN ? ELSE ? END) * INTERVAL '1 hour')) AS qualificadas,
                       EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.target_type=r.target_type AND b.target_id=r.target_id AND b.source='AUTOMATIC_PROTECTION' AND b.active) AS auto_active,
                       EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.target_type=r.target_type AND b.target_id=r.target_id AND b.source='ADMIN_RESTRICTION' AND b.active) AS admin_active,
                       min(r.created_at) AS oldest
                  FROM public.content_reports r
                  LEFT JOIN public.oscs o ON r.target_type='OSC' AND o.id=r.target_id
                  LEFT JOIN public.post p ON r.target_type='POST' AND p.id=r.target_id
                 WHERE r.status='PENDING' """ + typeFilter + """
                 GROUP BY r.target_type, r.target_id, r.content_kind, o.nome, p.titulo
                 ORDER BY auto_active DESC, oldest ASC
                 LIMIT ? OFFSET ?
                """;
        List<Object> args = new ArrayList<>(List.of(oscHours, volunteerHours, publicationHours));
        if (type != null) args.add(type);
        args.add(limit); args.add(offset);
        return jdbc.query(sql, this::queueRow, args.toArray());
    }

    public long queueCount(String type, String state) {
        if ("ACTIVE".equals(state)) {
            return type == null
                    ? jdbc.queryForObject("SELECT count(DISTINCT (target_type,target_id)) FROM public.content_moderation_blocks WHERE active", Long.class)
                    : jdbc.queryForObject("SELECT count(DISTINCT (target_type,target_id)) FROM public.content_moderation_blocks WHERE active AND target_type=?", Long.class, type);
        }
        if ("RESOLVED".equals(state)) {
            return type == null
                    ? jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM public.content_reports WHERE status<>'PENDING' GROUP BY target_type,target_id) q", Long.class)
                    : jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM public.content_reports WHERE status<>'PENDING' AND target_type=? GROUP BY target_type,target_id) q", Long.class, type);
        }
        return type == null
                ? jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM public.content_reports WHERE status='PENDING' GROUP BY target_type,target_id) q", Long.class)
                : jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM public.content_reports WHERE status='PENDING' AND target_type=? GROUP BY target_type,target_id) q", Long.class, type);
    }

    private List<AdminContentReportQueueItemDTO> queueResolved(String type, int limit, int offset) {
        String filter = type == null ? "" : " AND r.target_type=? ";
        String sql = """
                SELECT r.target_type,r.target_id,
                       coalesce(o.nome,p.titulo,'[conteudo indisponivel]') AS titulo,
                       coalesce(o.nome,p.titulo,'[conteudo indisponivel]') AS responsavel,
                       0::bigint AS pendentes,0::bigint AS qualificadas,
                       EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.target_type=r.target_type AND b.target_id=r.target_id AND b.source='AUTOMATIC_PROTECTION' AND b.active) AS auto_active,
                       EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.target_type=r.target_type AND b.target_id=r.target_id AND b.source='ADMIN_RESTRICTION' AND b.active) AS admin_active,
                       min(r.created_at) AS oldest
                  FROM public.content_reports r
                  LEFT JOIN public.oscs o ON r.target_type='OSC' AND o.id=r.target_id
                  LEFT JOIN public.post p ON r.target_type='POST' AND p.id=r.target_id
                 WHERE r.status<>'PENDING' """ + filter + """
                 GROUP BY r.target_type,r.target_id,o.nome,p.titulo
                 ORDER BY oldest DESC LIMIT ? OFFSET ?
                """;
        if (type == null) return jdbc.query(sql, this::queueRow, limit, offset);
        return jdbc.query(sql, this::queueRow, type, limit, offset);
    }

    private List<AdminContentReportQueueItemDTO> queueActive(String type, int oscHours,
            int publicationHours, int volunteerHours, int limit, int offset) {
        String filter = type == null ? "" : " AND b.target_type=? ";
        String sql = """
                SELECT b.target_type,b.target_id,
                       coalesce(o.nome,p.titulo,'[conteudo indisponivel]') AS titulo,
                       coalesce(o.nome,p.titulo,'[conteudo indisponivel]') AS responsavel,
                       (SELECT count(*) FROM public.content_reports r WHERE r.target_type=b.target_type AND r.target_id=b.target_id AND r.status='PENDING') AS pendentes,
                       (SELECT count(*) FROM public.content_reports r WHERE r.target_type=b.target_type AND r.target_id=b.target_id AND r.status='PENDING' AND r.created_at >= CURRENT_TIMESTAMP - ((CASE WHEN r.target_type='OSC' THEN ? WHEN r.content_kind='VOLUNTEERING' THEN ? ELSE ? END) * INTERVAL '1 hour')) AS qualificadas,
                       bool_or(b.source='AUTOMATIC_PROTECTION') AS auto_active,
                       bool_or(b.source='ADMIN_RESTRICTION') AS admin_active,
                       min(b.created_at) AS oldest
                  FROM public.content_moderation_blocks b
                  LEFT JOIN public.oscs o ON b.target_type='OSC' AND o.id=b.target_id
                  LEFT JOIN public.post p ON b.target_type='POST' AND p.id=b.target_id
                 WHERE b.active """ + filter + """
                 GROUP BY b.target_type,b.target_id,o.nome,p.titulo
                 ORDER BY oldest ASC LIMIT ? OFFSET ?
                """;
        List<Object> args = new ArrayList<>(List.of(oscHours, volunteerHours, publicationHours));
        if (type != null) args.add(type);
        args.add(limit); args.add(offset);
        return jdbc.query(sql, this::queueRow, args.toArray());
    }

    public AdminContentReportDetailDTO detail(String type, int id) {
        List<AdminContentReportDetailDTO> target = jdbc.query("""
                SELECT 'OSC' AS type, id AS target_id, nome AS title, descricao AS body,
                       (status='ATIVA' AND data_arquivamento IS NULL) AS available,
                       EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.target_type='OSC' AND b.target_id=oscs.id AND b.source='AUTOMATIC_PROTECTION' AND b.active) AS auto_active
                  FROM public.oscs WHERE ?='OSC' AND id=?
                UNION ALL
                SELECT 'POST', p.id, p.titulo, coalesce(p.conteudo,''),
                       (p.ativo AND o.status='ATIVA' AND o.data_arquivamento IS NULL),
                       EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.target_type='POST' AND b.target_id=p.id AND b.source='AUTOMATIC_PROTECTION' AND b.active)
                  FROM public.post p JOIN public.oscs o ON o.id=p.id_osc WHERE ?='POST' AND p.id=?
                """, (rs, row) -> new AdminContentReportDetailDTO(
                rs.getString("type"), rs.getInt("target_id"), rs.getString("title"), rs.getString("body"),
                rs.getBoolean("available"), rs.getBoolean("auto_active"), List.of(), List.of(), List.of()), type, id, type, id);
        if (target.isEmpty()) return null;
        List<AdminContentReportDetailDTO.Denuncia> reports = jdbc.query("""
                SELECT id, reason, details, status, created_at FROM public.content_reports
                 WHERE target_type=? AND target_id=? ORDER BY created_at, id
                """, (rs, row) -> new AdminContentReportDetailDTO.Denuncia(
                rs.getLong("id"), rs.getString("reason"), rs.getString("details"), rs.getString("status"),
                instant(rs.getTimestamp("created_at"))), type, id);
        AdminContentReportDetailDTO item = target.getFirst();
        List<AdminContentReportDetailDTO.Revisao> history = jdbc.query("""
                SELECT decision, justification, actor_profile, occurred_at FROM public.content_report_review_history
                 WHERE target_type=? AND target_id=? ORDER BY occurred_at, id
                """, (rs, row) -> new AdminContentReportDetailDTO.Revisao(
                rs.getString("decision"), rs.getString("justification"), rs.getString("actor_profile"),
                instant(rs.getTimestamp("occurred_at"))), type, id);
        List<AdminContentReportDetailDTO.Bloqueio> restrictions = jdbc.query("""
                SELECT id,source,content_kind,reason,created_at
                  FROM public.content_moderation_blocks
                 WHERE target_type=? AND target_id=? AND active
                 ORDER BY created_at,id
                """, (rs, row) -> new AdminContentReportDetailDTO.Bloqueio(
                rs.getLong("id"), rs.getString("source"), rs.getString("content_kind"),
                rs.getString("reason"), instant(rs.getTimestamp("created_at"))), type, id);
        return new AdminContentReportDetailDTO(item.tipo(), item.alvoId(), item.titulo(), item.conteudo(),
                item.disponivel(), item.protecaoAutomaticaAtiva(), reports, history, restrictions);
    }

    public Long decide(String type, int id, String decision, String justification, boolean restrictVisibility,
            AdminActor actor, Instant reviewedAt) {
        String contentKind = "UPHOLD".equals(decision)
                ? jdbc.queryForObject("SELECT max(content_kind) FROM public.content_reports WHERE target_type=? AND target_id=? AND status='PENDING'", String.class, type, id)
                : null;
        String reportStatus = switch (decision) {
            case "DISMISS" -> "DISMISSED";
            case "UPHOLD" -> "UPHELD";
            default -> throw new IllegalArgumentException("Unsupported report decision status.");
        };
        int updated = jdbc.update("""
                UPDATE public.content_reports SET status=?, reviewed_at=?
                 WHERE target_type=? AND target_id=? AND status='PENDING'
                """, reportStatus, Timestamp.from(reviewedAt), type, id);
        if (updated == 0) return null;
        if ("UPHOLD".equals(decision) && restrictVisibility) {
            jdbc.update("""
                    INSERT INTO public.content_moderation_blocks
                      (target_type, target_id, content_kind, source, reason, created_by_user_id)
                    VALUES (?, ?, ?, 'ADMIN_RESTRICTION', ?, ?)
                    ON CONFLICT (target_type, target_id, source) WHERE active DO NOTHING
                    """, type, id, contentKind, justification, actor.userId());
        }
        return jdbc.queryForObject("""
                INSERT INTO public.content_report_review_history
                  (target_type, target_id, decision, justification, actor_user_id_snapshot, actor_profile)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """,Long.class,type,id,decision,justification,actor.userId(),actor.profile().name());
    }

    public int createPreventiveRestriction(String type, int id, String contentKind,
            String justification, AdminActor actor) {
        return jdbc.update("""
                INSERT INTO public.content_moderation_blocks
                  (target_type,target_id,content_kind,source,reason,created_by_user_id)
                VALUES(?,?,?,'ADMIN_RESTRICTION',?,?)
                ON CONFLICT (target_type,target_id,source) WHERE active DO NOTHING
                """, type, id, contentKind, justification, actor.userId());
    }

    public boolean restoreRestriction(String type, int id, long blockId,
            String justification, AdminActor actor) {
        List<String> sources = jdbc.query("""
                SELECT source FROM public.content_moderation_blocks
                 WHERE id=? AND target_type=? AND target_id=? AND active FOR UPDATE
                """, (rs, row) -> rs.getString("source"), blockId, type, id);
        if (sources.isEmpty()) return false;
        int ended = jdbc.update("""
                UPDATE public.content_moderation_blocks
                   SET active=false,ended_at=CURRENT_TIMESTAMP,ended_by_user_id=?
                 WHERE id=? AND target_type=? AND target_id=? AND active
                """, actor.userId(), blockId, type, id);
        if (ended != 1) return false;
        jdbc.update("""
                INSERT INTO public.content_report_review_history
                  (target_type,target_id,decision,justification,actor_user_id_snapshot,actor_profile)
                VALUES(?,?,'RESTORE',?,?,?)
                """, type, id, justification, actor.userId(), actor.profile().name());
        return true;
    }

    private AdminContentReportQueueItemDTO queueRow(ResultSet rs, int row) throws SQLException {
        return new AdminContentReportQueueItemDTO(rs.getString("target_type"), rs.getInt("target_id"),
                rs.getString("titulo"), rs.getString("responsavel"), rs.getLong("pendentes"),
                rs.getLong("qualificadas"), rs.getBoolean("auto_active"), rs.getBoolean("admin_active"),
                instant(rs.getTimestamp("oldest")));
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
