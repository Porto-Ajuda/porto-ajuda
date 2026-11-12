package com.portoajuda.aplicacao_osc.admin;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inserts sanitized business-operation events in the caller's transaction. */
@Service
public class AdminOperationAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuthorizationService authorization;

    public AdminOperationAuditService(
            JdbcTemplate jdbcTemplate,
            AdminAuthorizationService authorization) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorization = authorization;
    }

    @Transactional
    public void record(
            AdminActor actor,
            AdminPermission permission,
            String action,
            String targetType,
            String targetId,
            String reason,
            String previousState,
            String newState) {
        record(actor, permission, action, targetType, targetId, reason,
                null, previousState, newState);
    }

    @Transactional
    public void record(
            AdminActor actor,
            AdminPermission permission,
            String action,
            String targetType,
            String targetId,
            String reason,
            AdminProfile affectedProfile,
            String previousState,
            String newState) {
        if (!authorization.isEnforcementEnabled()) {
            return;
        }
        if (actor == null || actor.userId() == null || actor.sessionId() == null
                || actor.profile() == null || permission == null) {
            throw new IllegalStateException("Contexto administrativo auditável ausente.");
        }
        String normalizedReason = authorization.requireReason(reason);
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("A justificativa da operação administrativa é obrigatória.");
        }
        int inserted = jdbcTemplate.update("""
                INSERT INTO public.admin_operation_audit_events (
                    id, occurred_at, actor_user_id_snapshot, actor_profile, affected_profile,
                    actor_session_id, actor_source, permission_name, action,
                    target_type, target_id, previous_state_summary,
                    new_state_summary, reason, correlation_id
                ) VALUES (?, ?, ?, ?, ?, ?, 'ADMIN_SESSION', ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), Timestamp.from(Instant.now()), actor.userId(), actor.profile().name(),
                affectedProfile == null ? null : affectedProfile.name(), actor.sessionId(),
                permission.name(), bounded(action, 60),
                bounded(targetType, 40), bounded(targetId, 100),
                boundedNullable(previousState, 500), boundedNullable(newState, 500),
                bounded(normalizedReason, 2000), UUID.randomUUID());
        if (inserted != 1) {
            throw new IllegalStateException("A auditoria da operação administrativa não foi persistida.");
        }
    }

    @Transactional
    public void recordLifecycle(
            Integer actorUserId,
            String actorSource,
            AdminProfile affectedProfile,
            String action,
            String targetId,
            String reason) {
        if (!authorization.isEnforcementEnabled()) {
            return;
        }
        String normalizedReason = AdminReasonSanitizer.sanitize(reason);
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("A justificativa da operação administrativa é obrigatória.");
        }
        int inserted = jdbcTemplate.update("""
                INSERT INTO public.admin_operation_audit_events (
                    id, occurred_at, actor_user_id_snapshot, actor_profile, affected_profile,
                    actor_session_id, actor_source, permission_name, action,
                    target_type, target_id, previous_state_summary,
                    new_state_summary, reason, correlation_id
                ) VALUES (?, ?, ?, NULL, ?, NULL, ?, 'ACCOUNT_LIFECYCLE', ?,
                          'ADMIN_ASSIGNMENT', ?, NULL, 'REVOKED', ?, ?)
                """,
                UUID.randomUUID(), Timestamp.from(Instant.now()), actorUserId,
                affectedProfile == null ? null : affectedProfile.name(),
                bounded(actorSource, 24), bounded(action, 60), bounded(targetId, 100),
                bounded(normalizedReason, 2000), UUID.randomUUID());
        if (inserted != 1) {
            throw new IllegalStateException("A auditoria do ciclo de conta não foi persistida.");
        }
    }

    private String bounded(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Campo de auditoria inválido.");
        }
        return value;
    }

    private String boundedNullable(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("Resumo de auditoria excede o limite permitido.");
        }
        return value;
    }
}
