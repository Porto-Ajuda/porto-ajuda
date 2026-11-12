package com.portoajuda.aplicacao_osc.admin;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service("adminAuthorization")
public class AdminAuthorizationService {

    private final AdminAssignmentRepository assignmentRepository;
    private final boolean enforcementEnabled;

    public AdminAuthorizationService(
            AdminAssignmentRepository assignmentRepository,
            @Value("${app.admin.rbac.enforcement-enabled:false}") boolean enforcementEnabled) {
        this.assignmentRepository = assignmentRepository;
        this.enforcementEnabled = enforcementEnabled;
    }

    public boolean isEnforcementEnabled() {
        return enforcementEnabled;
    }

    public boolean hasValidPrivilegedSession(Authentication authentication) {
        if (!hasBaseSession(authentication)) {
            return false;
        }
        if (!enforcementEnabled) {
            return true;
        }
        Integer userId = userId(authentication);
        if (userId == null || !hasTrustedSessionDetails(authentication)) {
            return false;
        }
        try {
            return assignmentRepository.findActiveByUserId(userId).isPresent();
        } catch (DataAccessException unavailable) {
            return false;
        }
    }

    public boolean hasPermission(Authentication authentication, AdminPermission permission) {
        if (permission == null || !hasBaseSession(authentication)) {
            return false;
        }
        if (!enforcementEnabled) {
            return legacyPermission(permission);
        }
        Integer userId = userId(authentication);
        if (userId == null || !hasTrustedSessionDetails(authentication)) {
            return false;
        }
        try {
            return activeAssignmentForCurrentTransaction(userId)
                    .map(assignment -> AdminPermissionCatalog.grants(assignment.profile(), permission))
                    .orElse(false);
        } catch (DataAccessException unavailable) {
            return false;
        }
    }

    public boolean hasAnyPermission(Authentication authentication, AdminPermission... permissions) {
        if (permissions == null) {
            return false;
        }
        for (AdminPermission permission : permissions) {
            if (hasPermission(authentication, permission)) {
                return true;
            }
        }
        return false;
    }

    public AdminActor requirePermission(AdminPermission permission) {
        Authentication authentication = currentAuthentication();
        if (!hasPermission(authentication, permission)) {
            throw new AccessDeniedException("Permissão administrativa insuficiente.");
        }
        return actor(authentication).orElseThrow(
                () -> new AccessDeniedException("Sessão administrativa inválida."));
    }

    public AdminActor requireValidPrivilegedSession() {
        Authentication authentication = currentAuthentication();
        if (!hasValidPrivilegedSession(authentication)) {
            throw new AccessDeniedException("Sessão administrativa ou atribuição inválida.");
        }
        return actor(authentication).orElseThrow(
                () -> new AccessDeniedException("Sessão administrativa inválida."));
    }

    public String requireReason(String reason) {
        if (enforcementEnabled && reason != null && reason.length() > 2000) {
            throw new AccessDeniedException("A justificativa administrativa excede o limite permitido.");
        }
        String normalized = AdminReasonSanitizer.sanitize(reason);
        if (enforcementEnabled && normalized.isEmpty()) {
            throw new AccessDeniedException("A justificativa é obrigatória para esta operação administrativa.");
        }
        return normalized;
    }

    public boolean isEligibleForPrivilegedSession(Usuario user) {
        if (user == null || user.getId() == null || !user.isEnabled()
                || !user.isEmailVerificado() || !user.isTelefoneVerificado()
                || user.getEmail() == null || user.getTelefone() == null
                || user.getTelefone().isBlank() || !hasRoleAdmin(user)) {
            return false;
        }
        if (!enforcementEnabled) {
            return true;
        }
        if (!hasEligibleVerifiedAdminContacts(user)) {
            return false;
        }
        try {
            return assignmentRepository.findActiveByUserId(user.getId()).isPresent();
        } catch (DataAccessException unavailable) {
            return false;
        }
    }

    public boolean hasEligibleVerifiedAdminContacts(Usuario user) {
        if (user == null || user.getEmail() == null || user.getEmail().valor().isBlank()
                || !user.isEmailVerificado() || !user.isTelefoneVerificado()) {
            return false;
        }
        String phone = user.getTelefone();
        if (phone == null || phone.isBlank() || !phone.trim().matches("\\+?[0-9\\s().-]+")) {
            return false;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 13 && digits.startsWith("55")) {
            digits = digits.substring(2);
        }
        return digits.matches("[1-9][0-9]9[0-9]{8}");
    }

    public AdminCapabilitiesDTO capabilities() {
        AdminActor actor = requireValidPrivilegedSession();
        if (!enforcementEnabled || actor.profile() == null) {
            throw new AccessDeniedException("RBAC administrativo ainda não está ativo.");
        }
        List<AdminPermission> permissions = AdminPermissionCatalog.permissionsFor(actor.profile())
                .stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        return new AdminCapabilitiesDTO(actor.profile(), permissions);
    }

    public Optional<AdminAssignment> activeAssignment(Integer userId) {
        if (!enforcementEnabled || userId == null) {
            return Optional.empty();
        }
        return assignmentRepository.findActiveByUserId(userId);
    }

    private boolean hasBaseSession(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        Usuario user = principal(authentication);
        return user != null && user.getId() != null && user.isEnabled()
                && hasRoleAdmin(user)
                && hasAuthority(authentication, "ADMIN_SESSION");
    }

    private boolean legacyPermission(AdminPermission permission) {
        return switch (permission) {
            case DASHBOARD_READ, ORG_READ_PRIVATE, ORG_DEACTIVATE, ORG_REACTIVATE,
                    ORG_RESTORE_ARCHIVED, CONTENT_READ, CONTENT_PUBLISH,
                    CONTENT_EDIT_NEWS, CONTENT_DELETE, SESSION_REVOKE_SELF -> true;
            default -> false;
        };
    }

    private Optional<AdminActor> actor(Authentication authentication) {
        Usuario user = principal(authentication);
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        java.util.UUID sessionId = authentication.getDetails() instanceof AdminAuthenticationDetails details
                ? details.sessionId()
                : null;
        AdminProfile profile = enforcementEnabled
                ? assignmentRepository.findActiveByUserId(user.getId()).map(AdminAssignment::profile).orElse(null)
                : null;
        return Optional.of(new AdminActor(user.getId(), sessionId, profile));
    }

    private Authentication currentAuthentication() {
        return org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
    }

    private Usuario principal(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof Usuario usuario
                ? usuario
                : null;
    }

    private Integer userId(Authentication authentication) {
        Usuario user = principal(authentication);
        return user == null ? null : user.getId();
    }

    private boolean hasRoleAdmin(Usuario user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private boolean hasTrustedSessionDetails(Authentication authentication) {
        return authentication.getDetails() instanceof AdminAuthenticationDetails details
                && details.sessionId() != null;
    }

    private Optional<AdminAssignment> activeAssignmentForCurrentTransaction(Integer userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return assignmentRepository.findActiveByUserIdForUpdate(userId);
        }
        return assignmentRepository.findActiveByUserId(userId);
    }
}
