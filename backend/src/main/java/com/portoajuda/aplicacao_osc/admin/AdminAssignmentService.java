package com.portoajuda.aplicacao_osc.admin;

import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAssignmentService {

    private final AdminAssignmentRepository assignments;
    private final AdminAuthorizationService authorization;
    private final AdminRbacInvariantService invariant;
    private final AdminOperationAuditService audit;
    private final AdminPrivilegedSessionService sessions;
    private final UsuarioRepository users;
    private final RoleRepository roles;

    public AdminAssignmentService(
            AdminAssignmentRepository assignments,
            AdminAuthorizationService authorization,
            AdminRbacInvariantService invariant,
            AdminOperationAuditService audit,
            AdminPrivilegedSessionService sessions,
            UsuarioRepository users,
            RoleRepository roles) {
        this.assignments = assignments;
        this.authorization = authorization;
        this.invariant = invariant;
        this.audit = audit;
        this.sessions = sessions;
        this.users = users;
        this.roles = roles;
    }

    @Transactional
    public AdminAssignmentDTO assignOrChange(AdminAssignmentRequest request) {
        ensureRbacEnabled();
        invariant.lockEligibilityChanges();
        AdminActor actor = authorization.requirePermission(AdminPermission.ADMIN_MANAGE);
        rejectSelfChange(actor, request.userId());
        String reason = authorization.requireReason(request.reason());

        Usuario target = users.findByIdForUpdate(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuário administrativo não encontrado."));
        requireEligibleAccount(target);

        AdminAssignment current = assignments.findActiveByUserIdForUpdate(target.getId()).orElse(null);
        if (current != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O usuário já possui uma atribuição administrativa ativa.");
        }

        ensureRoleAdmin(target);
        Instant now = Instant.now();
        AdminAssignment created = new AdminAssignment(
                UUID.randomUUID(), target.getId(), target.getId(), request.profile(),
                AdminAssignmentStatus.ACTIVE, now, actor.userId(), now, actor.userId(),
                null, reason);
        assignments.insert(created);
        sessions.revogarTodasDoUsuario(target.getId(), "ADMIN_REVOKED");
        audit.record(actor, AdminPermission.ADMIN_MANAGE, "ADMIN_ASSIGNMENT_GRANTED",
                "ADMIN_ASSIGNMENT", created.id().toString(), reason,
                request.profile(), null, request.profile().name());
        return AdminAssignmentDTO.from(created);
    }

    @Transactional
    public AdminAssignmentDTO changeProfile(
            UUID assignmentId,
            AdminAssignmentProfileRequest request) {
        ensureRbacEnabled();
        invariant.lockEligibilityChanges();
        AdminActor actor = authorization.requirePermission(AdminPermission.ADMIN_MANAGE);
        String reason = authorization.requireReason(request.reason());
        AdminAssignment current = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Atribuição administrativa não encontrada."));
        if (current.status() != AdminAssignmentStatus.ACTIVE || current.userId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A atribuição administrativa não está ativa.");
        }
        rejectSelfChange(actor, current.userId());
        if (current.profile() == request.profile()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O usuário já possui esse perfil administrativo.");
        }
        Usuario target = users.findByIdForUpdate(current.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuário administrativo não encontrado."));
        requireEligibleAccount(target);
        if (current.profile() == AdminProfile.SUPER_ADMIN
                && request.profile() != AdminProfile.SUPER_ADMIN) {
            invariant.assertMayRemoveEligibility(target.getId());
        }

        Instant now = Instant.now();
        int revoked = assignments.revoke(current.id(), actor.userId(), reason, now);
        if (revoked != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Atribuição administrativa alterada por outra operação.");
        }
        AdminAssignment changed = new AdminAssignment(
                UUID.randomUUID(), target.getId(), target.getId(), request.profile(),
                AdminAssignmentStatus.ACTIVE, now, actor.userId(), now, actor.userId(),
                null, reason);
        assignments.insert(changed);
        sessions.revogarTodasDoUsuario(target.getId(), "ADMIN_REVOKED");
        audit.record(actor, AdminPermission.ADMIN_MANAGE, "ADMIN_ASSIGNMENT_CHANGED",
                "ADMIN_ASSIGNMENT", changed.id().toString(), reason,
                request.profile(), current.profile().name(), request.profile().name());
        return AdminAssignmentDTO.from(changed);
    }

    @Transactional
    public AdminAssignmentDTO revoke(UUID assignmentId, AdminAssignmentRevokeRequest request) {
        ensureRbacEnabled();
        invariant.lockEligibilityChanges();
        AdminActor actor = authorization.requirePermission(AdminPermission.ADMIN_MANAGE);
        String reason = authorization.requireReason(request.reason());
        AdminAssignment current = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Atribuição administrativa não encontrada."));
        if (current.status() != AdminAssignmentStatus.ACTIVE || current.userId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A atribuição administrativa não está ativa.");
        }
        rejectSelfChange(actor, current.userId());
        if (current.profile() == AdminProfile.SUPER_ADMIN) {
            invariant.assertMayRemoveEligibility(current.userId());
        }
        int changed = assignments.revoke(
                assignmentId, actor.userId(), reason, Instant.now());
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A atribuição administrativa foi alterada em outra operação.");
        }

        Usuario target = users.findByIdForUpdate(current.userId()).orElse(null);
        if (target != null) {
            removeRoleAdmin(target);
        }
        sessions.revogarTodasDoUsuario(current.userId(), "ADMIN_REVOKED");
        audit.record(actor, AdminPermission.ADMIN_MANAGE,
                "ADMIN_ASSIGNMENT_REVOKED", "ADMIN_ASSIGNMENT", assignmentId.toString(),
                reason, current.profile(), current.profile().name(),
                AdminAssignmentStatus.REVOKED.name());
        return new AdminAssignmentDTO(
                current.id(), current.userId(), current.profile(), AdminAssignmentStatus.REVOKED,
                current.grantedAt(), current.grantedByUserId(), Instant.now(), actor.userId(),
                Instant.now(), reason);
    }

    @Transactional(readOnly = true)
    public List<AdminAssignmentDTO> list(int limit, int offset) {
        authorization.requirePermission(AdminPermission.ADMIN_MANAGE);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        return assignments.findAll(safeLimit, safeOffset).stream()
                .map(AdminAssignmentDTO::from)
                .toList();
    }

    /** Called by scheduled cleanup before deleting an account that may have legacy RBAC rows. */
    @Transactional
    public boolean prepareForScheduledCleanup(Integer userId) {
        if (!authorization.isEnforcementEnabled() || userId == null) {
            return true;
        }
        invariant.lockEligibilityChanges();
        AdminAssignment current = assignments.findActiveByUserIdForUpdate(userId).orElse(null);
        if (current == null) {
            return assignments.countActiveSuperAdmins() > 0
                    && assignments.countEligibleActiveSuperAdmins() > 0;
        }
        if (!invariant.canRemoveEligibility(userId)) {
            return false;
        }
        Instant now = Instant.now();
        String reason = AdminReasonSanitizer.sanitize(
                "Revogação automática antes da exclusão agendada da conta.");
        assignments.revoke(current.id(), null, reason, now);
        users.findByIdForUpdate(userId).ifPresent(this::removeRoleAdmin);
        sessions.revogarTodasDoUsuario(userId, "ADMIN_REVOKED");
        audit.recordLifecycle(null, "SYSTEM", current.profile(),
                "ADMIN_ASSIGNMENT_REVOKED_ACCOUNT_CLEANUP", userId.toString(), reason);
        invariant.assertAtLeastOneEligibleSuperAdminRemains();
        return true;
    }

    public void rejectSelfServiceDeletionWhenAssigned(Integer userId) {
        if (!authorization.isEnforcementEnabled() || userId == null) {
            return;
        }
        invariant.lockEligibilityChanges();
        if (assignments.findActiveByUserId(userId).isPresent()) {
            throw new AccessDeniedException(
                    "Uma atribuição administrativa ativa deve ser revogada por outro SUPER_ADMIN antes do encerramento da conta.");
        }
    }

    private void ensureRbacEnabled() {
        if (!authorization.isEnforcementEnabled()) {
            throw new AccessDeniedException("A gestão RBAC ainda não foi ativada.");
        }
    }

    private void rejectSelfChange(AdminActor actor, Integer targetUserId) {
        if (actor.userId().equals(targetUserId)) {
            throw new AccessDeniedException("Não é permitido alterar a própria atribuição administrativa.");
        }
    }

    private void requireEligibleAccount(Usuario user) {
        if (!user.isEnabled() || !user.isEmailVerificado() || !user.isTelefoneVerificado()
                || user.getEmail() == null || user.getTelefone() == null
                || user.getTelefone().isBlank()
                || !authorization.hasEligibleVerifiedAdminContacts(user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A conta precisa estar ativa e ter email e telefone verificados para receber acesso administrativo.");
        }
    }

    private void ensureRoleAdmin(Usuario user) {
        boolean hasAdminRole = user.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (hasAdminRole) {
            return;
        }
        Role adminRole = roles.findByNome("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role ADMIN não encontrada."));
        user.getRoles().add(adminRole);
        users.save(user);
    }

    private void removeRoleAdmin(Usuario user) {
        user.getRoles().removeIf(role -> "ADMIN".equals(role.getNome()));
        users.save(user);
    }
}
