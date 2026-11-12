package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.dto.response.AdminDashboardActivityDTO;
import com.portoajuda.aplicacao_osc.dto.response.AdminDashboardOverviewDTO;
import com.portoajuda.aplicacao_osc.entity.AdminSecurityAuditEvent;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.repository.AdminSecurityAuditEventRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminDashboardService {

    private final UsuarioRepository usuarioRepository;
    private final OscRepository oscRepository;
    private final PostRepository postRepository;
    private final VagaRepository vagaRepository;
    private final AdminSecurityAuditEventRepository auditRepository;
    private final AdminAuthorizationService authorization;

    public AdminDashboardService(
            UsuarioRepository usuarioRepository,
            OscRepository oscRepository,
            PostRepository postRepository,
            VagaRepository vagaRepository,
            AdminSecurityAuditEventRepository auditRepository,
            AdminAuthorizationService authorization) {
        this.usuarioRepository = usuarioRepository;
        this.oscRepository = oscRepository;
        this.postRepository = postRepository;
        this.vagaRepository = vagaRepository;
        this.auditRepository = auditRepository;
        this.authorization = authorization;
    }

    public AdminDashboardOverviewDTO obterVisaoGeral() {
        authorization.requirePermission(AdminPermission.DASHBOARD_READ);
        final long users;
        final long activeOrganizations;
        final long inactiveOrganizations;
        final long archivedOrganizations;
        final long publications;
        final long volunteerOpportunities;
        try {
            users = usuarioRepository.count();
            activeOrganizations = oscRepository.countByStatusAndDataArquivamentoIsNull(StatusOsc.ATIVA);
            inactiveOrganizations = oscRepository.countByStatusAndDataArquivamentoIsNull(StatusOsc.INATIVA);
            archivedOrganizations = oscRepository.countByDataArquivamentoIsNotNull();
            publications = postRepository.count();
            volunteerOpportunities = vagaRepository.count();
        } catch (DataAccessException unavailable) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Os indicadores administrativos estão indisponíveis.");
        }

        boolean activityAvailable = true;
        List<AdminDashboardActivityDTO> activity = List.of();
        try {
            activity = auditRepository
                    .findTop10ByEventTypeStartingWithOrderByOccurredAtDesc("ADMIN_")
                    .stream()
                    .map(this::toActivity)
                    .toList();
        } catch (DataAccessException unavailable) {
            activityAvailable = false;
        }

        return new AdminDashboardOverviewDTO(
                users,
                activeOrganizations,
                inactiveOrganizations,
                archivedOrganizations,
                publications,
                volunteerOpportunities,
                Instant.now(),
                activityAvailable,
                activity);
    }

    private AdminDashboardActivityDTO toActivity(AdminSecurityAuditEvent event) {
        return new AdminDashboardActivityDTO(
                descricaoSegura(event.getEventType()),
                event.getOccurredAt());
    }

    private String descricaoSegura(String eventType) {
        if (eventType == null) return "Evento de segurança administrativa";
        return switch (eventType) {
            case "ADMIN_SESSION_ISSUED" -> "Sessão administrativa iniciada";
            case "ADMIN_AUTH_CHALLENGE_ISSUED" -> "Desafio de autenticação administrativa emitido";
            case "ADMIN_AUTH_MFA_VERIFIED" -> "Autenticação multifator administrativa concluída";
            case "ADMIN_AUTH_CREDENTIAL_FAILURE", "ADMIN_AUTH_CODE_FAILURE" ->
                    "Falha de autenticação administrativa";
            case "ADMIN_AUTH_ACCOUNT_LOCKED", "ADMIN_AUTH_CHALLENGE_LOCKED" ->
                    "Tentativa de autenticação bloqueada por segurança";
            case "ADMIN_SESSION_REVOKED_LOGOUT" -> "Sessão administrativa encerrada";
            default -> eventType.startsWith("ADMIN_SESSION_REVOKED_")
                    ? "Sessão administrativa revogada"
                    : "Evento de segurança administrativa";
        };
    }
}
