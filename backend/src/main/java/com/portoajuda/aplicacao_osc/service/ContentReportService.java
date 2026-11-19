package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.dto.request.RequestContentReportDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestContentReportDecisionDTO;
import com.portoajuda.aplicacao_osc.dto.response.AdminContentReportDetailDTO;
import com.portoajuda.aplicacao_osc.dto.response.AdminContentReportQueueItemDTO;
import com.portoajuda.aplicacao_osc.dto.response.ContentReportDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.ContentReportJdbcRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContentReportService {
    private final ContentReportJdbcRepository reports;
    private final OscRepository oscs;
    private final PostRepository posts;
    private final UsuarioRepository users;
    private final AutenticacaoAbusoUsuarioService abuse;
    private final AdminAuthCodeSecurity identifierSecurity;
    private final AdminAuthorizationService admin;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final boolean enabled;
    private final int oscThreshold, postThreshold, volunteerThreshold;
    private final int oscWindow, postWindow, volunteerWindow;
    @Autowired(required = false)
    private OscMembrosRepository members;
    @Autowired(required = false)
    private ModerationPointsService moderationPoints;
    @Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    public ContentReportService(
            ContentReportJdbcRepository reports, OscRepository oscs, PostRepository posts,
            UsuarioRepository users, AutenticacaoAbusoUsuarioService abuse,
            AdminAuthCodeSecurity identifierSecurity, AdminAuthorizationService admin, ApplicationEventPublisher events,
            @Value("${app.content-reporting.enabled:false}") boolean enabled,
            @Value("${app.content-reporting.organization-threshold:10}") int oscThreshold,
            @Value("${app.content-reporting.publication-threshold:5}") int postThreshold,
            @Value("${app.content-reporting.volunteer-threshold:5}") int volunteerThreshold,
            @Value("${app.content-reporting.organization-window-hours:48}") int oscWindow,
            @Value("${app.content-reporting.publication-window-hours:24}") int postWindow,
            @Value("${app.content-reporting.volunteer-window-hours:24}") int volunteerWindow) {
        this(reports, oscs, posts, users, abuse, identifierSecurity, admin, events,
                enabled, oscThreshold, postThreshold, volunteerThreshold, oscWindow, postWindow, volunteerWindow,
                Clock.systemUTC());
    }

    ContentReportService(
            ContentReportJdbcRepository reports, OscRepository oscs, PostRepository posts,
            UsuarioRepository users, AutenticacaoAbusoUsuarioService abuse,
            AdminAuthCodeSecurity identifierSecurity, AdminAuthorizationService admin, ApplicationEventPublisher events,
            boolean enabled, int oscThreshold, int postThreshold, int volunteerThreshold,
            int oscWindow, int postWindow, int volunteerWindow, Clock clock) {
        this.reports = reports; this.oscs = oscs; this.posts = posts; this.users = users;
        this.abuse = abuse; this.identifierSecurity = identifierSecurity; this.admin = admin; this.events = events;
        this.clock = clock; this.enabled = enabled;
        this.oscThreshold = bound(oscThreshold, 1, 100); this.postThreshold = bound(postThreshold, 1, 100);
        this.volunteerThreshold = bound(volunteerThreshold, 1, 100); this.oscWindow = bound(oscWindow, 1, 168);
        this.postWindow = bound(postWindow, 1, 168); this.volunteerWindow = bound(volunteerWindow, 1, 168);
    }

    @Transactional
    public ContentReportDTO reportar(RequestContentReportDTO request, Usuario reporter, String remoteAddress) {
        requireEnabled();
        if (reporter == null || reporter.getId() == null || !reporter.isEnabled() || !reporter.isEmailVerificado())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "É necessário entrar em uma conta ativa com e-mail confirmado para denunciar.");
        if (request == null || request.tipo() == null || request.alvoId() == null || request.alvoId() < 1
                || request.motivo() == null || request.justificativa() == null
                || request.justificativa().isBlank() || request.justificativa().length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confira os dados da denúncia.");
        if (!abuse.permitirDenuncia(reporter.getId(), remoteAddress))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite de denúncias atingido. Tente novamente mais tarde.");

        boolean campaignReport=request.tipo()==RequestContentReportDTO.TipoAlvo.CAMPANHA;
        String type = request.tipo() == RequestContentReportDTO.TipoAlvo.OSC ? "OSC" : campaignReport ? "CAMPAIGN" : "POST";
        String contentKind = request.tipo() == RequestContentReportDTO.TipoAlvo.OSC ? "OSC"
                : campaignReport ? "CAMPAIGN" : request.tipo() == RequestContentReportDTO.TipoAlvo.VOLUNTARIADO ? "VOLUNTEERING" : "PUBLICATION";
        Osc osc; Post post = null;
        if ("OSC".equals(type)) {
            osc = oscs.findForUpdateById(request.alvoId()).orElseThrow(ContentReportService::inaccessible);
            requirePublicOrganization(osc);
        } else if(campaignReport) {
            if(request.alvoId()>Integer.MAX_VALUE||jdbc==null)throw inaccessible();
            List<Integer> organization=jdbc.query("""
                    SELECT osc_id FROM donation_campaigns
                     WHERE id=? AND state='ACTIVE' AND NOT moderation_suspended
                       AND (deadline IS NULL OR deadline>CURRENT_TIMESTAMP)
                    """,(rs,n)->rs.getInt(1),request.alvoId());
            if(organization.isEmpty())throw inaccessible();
            osc=oscs.findForUpdateById(organization.getFirst()).orElseThrow(ContentReportService::inaccessible);
            requirePublicOrganization(osc);
            Integer locked=jdbc.query("SELECT id FROM donation_campaigns WHERE id=? AND state='ACTIVE' AND NOT moderation_suspended FOR UPDATE",
                    (rs,n)->rs.getInt(1),request.alvoId()).stream().findFirst().orElse(null);
            if(locked==null)throw inaccessible();
        } else {
            // Lock order matches content/application paths: organization first, then post.
            Post reference = posts.findById(request.alvoId()).orElseThrow(ContentReportService::inaccessible);
            if (reference.getOsc() == null || reference.getOsc().getId() == null) throw inaccessible();
            osc = oscs.findForUpdateById(reference.getOsc().getId()).orElseThrow(ContentReportService::inaccessible);
            requirePublicOrganization(osc);
            post = posts.findForUpdateById(request.alvoId()).orElseThrow(ContentReportService::inaccessible);
            if (post.getOsc() == null || !osc.getId().equals(post.getOsc().getId()) || !post.isAtivo()) throw inaccessible();
            if (request.tipo() == RequestContentReportDTO.TipoAlvo.VOLUNTARIADO) {
                if (post.getTipo() != TipoPost.VOLUNTARIADO || !vagaPublica(post)) throw inaccessible();
            } else if (post.getTipo() == TipoPost.VOLUNTARIADO) throw inaccessible();
        }

        String details = sanitize(request.justificativa());
        String reporterKey = identifierSecurity.hashIdentifier("content-report-reporter", String.valueOf(reporter.getId()));
        if (reports.hasPendingReport(type, request.alvoId(), reporterKey))
            throw pendingReportConflict();
        Instant latestReviewedAt = reports.latestReviewedAt(type, request.alvoId(), reporterKey);
        Instant eligibleAt = latestReviewedAt == null ? null : latestReviewedAt.plus(Duration.ofDays(7));
        if (eligibleAt != null && clock.instant().isBefore(eligibleAt))
            throw cooldownConflict();
        if (!reports.insert(type, request.alvoId(), contentKind, reporterKey, request.motivo().name(), details))
            throw pendingReportConflict();

        int window = "OSC".equals(type) ? oscWindow : request.tipo() == RequestContentReportDTO.TipoAlvo.VOLUNTARIADO ? volunteerWindow : postWindow;
        int threshold = "OSC".equals(type) ? oscThreshold : request.tipo() == RequestContentReportDTO.TipoAlvo.VOLUNTARIADO ? volunteerThreshold : postThreshold;
        long count = reports.qualifyingCount(type, request.alvoId(), contentKind, window);
        if (count >= threshold && reports.activateAutomaticProtection(type, request.alvoId(), contentKind) > 0) {
            Integer ownerId = members == null ? null : members.findByOsc_IdAndCargoOsc(osc.getId(), CargoOsc.CRIADOR)
                    .map(membership -> membership.getUsuario() == null ? null : membership.getUsuario().getId())
                    .orElse(null);
            if (ownerId == null && osc.getUsuario() != null) ownerId = osc.getUsuario().getId();
            if (ownerId != null) users.findById(ownerId).ifPresent(owner ->
                    events.publishEvent(new ContentProtectionActivatedEvent(owner.getEmail().valor(), osc.getNome())));
        }
        return new ContentReportDTO("PENDING", "Denúncia recebida para análise.", java.time.Instant.now());
    }

    @Transactional(readOnly = true)
    public List<AdminContentReportQueueItemDTO> queue(String type, String requestedState, int requestedLimit, int requestedOffset) {
        requireEnabled(); admin.requirePermission(AdminPermission.REPORT_READ);
        String normalized = normalizeType(type);
        String state = requestedState == null || requestedState.isBlank() ? "PENDING" : requestedState.trim().toUpperCase(Locale.ROOT);
        if (!state.equals("PENDING") && !state.equals("ACTIVE") && !state.equals("RESOLVED")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de revisão inválido.");
        int limit = bound(requestedLimit, 1, 50), offset = Math.max(0, requestedOffset);
        return reports.queue(normalized, state, oscWindow, postWindow, volunteerWindow, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countAlvos(String type, String state) {
        requireEnabled(); admin.requirePermission(AdminPermission.REPORT_READ);
        String normalized = normalizeType(type);
        String canonicalState = state == null || state.isBlank() ? "PENDING" : state.trim().toUpperCase(Locale.ROOT);
        if (!canonicalState.equals("PENDING") && !canonicalState.equals("ACTIVE") && !canonicalState.equals("RESOLVED")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de revisão inválido.");
        return reports.queueCount(normalized, canonicalState);
    }

    @Transactional(readOnly = true)
    public AdminContentReportDetailDTO detail(String type, Integer id) {
        requireEnabled(); admin.requirePermission(AdminPermission.REPORT_READ);
        String normalized = normalizeTypeRequired(type);
        AdminContentReportDetailDTO detail = reports.detail(normalized, id == null ? -1 : id);
        if (detail == null) throw inaccessible();
        return detail;
    }

    @Transactional
    public void decidir(String type, Integer id, RequestContentReportDecisionDTO request) {
        requireEnabled();
        AdminActor actor = admin.requirePermission(AdminPermission.REPORT_REVIEW);
        if (request == null || id == null || id < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma decisão válida.");
        String normalized = normalizeTypeRequired(type);
        if (request.decisao() == RequestContentReportDecisionDTO.Decisao.RESTORE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecione uma restri\u00e7\u00e3o de visibilidade espec\u00edfica para restaurar.");
        if (request.restringirVisibilidade()
                && request.decisao() != RequestContentReportDecisionDTO.Decisao.UPHOLD)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A restri\u00e7\u00e3o de visibilidade s\u00f3 pode ser escolhida em uma decis\u00e3o procedente.");
        lockTarget(normalized, id);
        String justification = sanitize(request.justificativa());
        if (justification.length() < 8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A justificativa deve ter ao menos 8 caracteres.");
        if (request.decisao() == RequestContentReportDecisionDTO.Decisao.UPHOLD)
            admin.requirePermission(AdminPermission.REPORT_RESOLVE);
        if (request.decisao() == RequestContentReportDecisionDTO.Decisao.UPHOLD
                && moderationPoints != null && moderationPoints.isEnabled()
                && (request.pontos() == null || request.pontos() < 1 || request.pontos() > 3))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Escolha de 1 a 3 pontos para esta infração procedente.");
        if (request.restringirVisibilidade())
            admin.requirePermission(AdminPermission.CONTENT_DEACTIVATE);
        Long decisionId = reports.decide(normalized, id, request.decisao().name(), justification,
                request.restringirVisibilidade(), actor, clock.instant());
        if (decisionId == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "As denúncias deste conteúdo já foram analisadas.");
        if (request.decisao() == RequestContentReportDecisionDTO.Decisao.UPHOLD
                && moderationPoints != null && moderationPoints.isEnabled())
            moderationPoints.applyUpheldDecision(decisionId,request.pontos(),justification,actor);
    }

    @Transactional
    public void ocultarEnquantoPendente(String type, Integer id, String justification) {
        requireEnabled();
        AdminActor actor = admin.requirePermission(AdminPermission.REPORT_REVIEW);
        admin.requirePermission(AdminPermission.CONTENT_DEACTIVATE);
        if (id == null || id < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o conte\u00fado.");
        String normalized = normalizeTypeRequired(type);
        String reason = sanitize(justification);
        if (reason.length() < 8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A justificativa deve ter ao menos 8 caracteres.");
        lockTarget(normalized, id);
        if (!reports.hasPendingReports(normalized, id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "N\u00e3o h\u00e1 den\u00fancias pendentes para ocultar este conte\u00fado durante a an\u00e1lise.");
        String kind = reports.pendingContentKind(normalized, id);
        if (kind == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "As den\u00fancias mudaram. Atualize a fila.");
        if (reports.createPreventiveRestriction(normalized, id, kind, reason, actor) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "J\u00e1 existe uma restri\u00e7\u00e3o administrativa ativa para este conte\u00fado.");
    }

    @Transactional
    public void restaurarRestricao(String type, Integer id, Long blockId, String justification) {
        requireEnabled();
        AdminActor actor = admin.requirePermission(AdminPermission.CONTENT_REACTIVATE);
        if (id == null || id < 1 || blockId == null || blockId < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma restri\u00e7\u00e3o de visibilidade v\u00e1lida.");
        String normalized = normalizeTypeRequired(type);
        String reason = sanitize(justification);
        if (reason.length() < 8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A justificativa deve ter ao menos 8 caracteres.");
        lockTarget(normalized, id);
        if (!reports.restoreRestriction(normalized, id, blockId, reason, actor))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "A restri\u00e7\u00e3o ativa n\u00e3o foi encontrada para este conte\u00fado.");
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String type, Integer id) {
        return enabled && id != null && reports.blocked(type, id);
    }

    public boolean isEnabled() { return enabled; }

    @Transactional(readOnly = true)
    public boolean hasReportHistory(String type, Integer id) {
        return enabled && id != null && reports.hasReportHistory(type, id);
    }

    private void lockTarget(String type, Integer id) {
        if ("OSC".equals(type)) oscs.findForUpdateById(id).orElseThrow(ContentReportService::inaccessible);
        else if("CAMPAIGN".equals(type)) {
            if(jdbc==null)throw inaccessible();
            Integer oscId=jdbc.query("SELECT osc_id FROM donation_campaigns WHERE id=?",(rs,n)->rs.getInt(1),id).stream().findFirst().orElseThrow(ContentReportService::inaccessible);
            oscs.findForUpdateById(oscId).orElseThrow(ContentReportService::inaccessible);
            jdbc.query("SELECT id FROM donation_campaigns WHERE id=? FOR UPDATE",(rs,n)->rs.getLong(1),id).stream().findFirst().orElseThrow(ContentReportService::inaccessible);
        }
        else {
            if(moderationPoints!=null&&moderationPoints.isEnabled())moderationPoints.lockDecisionSubject(type,id);
            Post post = posts.findById(id).orElseThrow(ContentReportService::inaccessible);
            Osc osc = post.getOsc();
            if (osc == null) throw inaccessible();
            oscs.findForUpdateById(osc.getId()).orElseThrow(ContentReportService::inaccessible);
            posts.findForUpdateById(id).orElseThrow(ContentReportService::inaccessible);
        }
    }

    private void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O sistema de denúncias ainda não está habilitado.");
    }
    private static ResponseStatusException pendingReportConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Você já possui uma denúncia pendente para este conteúdo.");
    }
    private static ResponseStatusException cooldownConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Uma nova denúncia estará disponível 7 dias após a última análise.");
    }
    private static void requirePublicOrganization(Osc osc) {
        if (osc.isArquivada() || osc.getStatus() != StatusOsc.ATIVA) throw inaccessible();
    }
    private static boolean vagaPublica(Post post) {
        var vaga = post.getVagaVoluntariado();
        return vaga != null && vaga.getStatus() == StatusVaga.ABERTA && vaga.getQuantidadeVagas() != null
                && vaga.getQuantidadeVagas() > 0 && vaga.getDataFim() != null && !vaga.getDataFim().isBefore(LocalDate.now());
    }
    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return null;
        return normalizeTypeRequired(type);
    }
    private static String normalizeTypeRequired(String type) {
        String canonical = type.trim().toUpperCase(Locale.ROOT);
        if (canonical.equals("PUBLICACAO") || canonical.equals("VOLUNTARIADO")) canonical = "POST";
        if(canonical.equals("CAMPANHA"))canonical="CAMPAIGN";
        if (!canonical.equals("OSC") && !canonical.equals("POST") && !canonical.equals("CAMPAIGN")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de conteúdo inválido.");
        return canonical;
    }
    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        return clean.length() > 2000 ? clean.substring(0, 2000) : clean;
    }
    private static int bound(int value, int min, int max) { return Math.min(Math.max(value, min), max); }
    private static ResponseStatusException inaccessible() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conteúdo não encontrado ou indisponível."); }
}
