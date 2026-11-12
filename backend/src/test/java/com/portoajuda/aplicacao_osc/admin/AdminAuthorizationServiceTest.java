package com.portoajuda.aplicacao_osc.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.utils.Email;
import com.portoajuda.aplicacao_osc.entity.Role;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;

class AdminAuthorizationServiceTest {

    private final AdminAssignmentRepository assignments = mock(AdminAssignmentRepository.class);

    @Test
    void beanNameMatchesMethodSecurityExpressions() {
        assertEquals("adminAuthorization", AdminAuthorizationService.class.getAnnotation(Service.class).value());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roleAdminSemSessaoPrivilegiadaNaoAutorizaOperacao() {
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);
        var authentication = authentication(true, false, false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertFalse(authorization.hasPermission(authentication, AdminPermission.DASHBOARD_READ));
        assertThrows(AccessDeniedException.class,
                () -> authorization.requirePermission(AdminPermission.DASHBOARD_READ));
    }

    @Test
    void sessaoPrivilegiadaSemRoleAdminNaoAutorizaOperacao() {
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);
        var authentication = authentication(false, true, true);

        assertFalse(authorization.hasValidPrivilegedSession(authentication));
        assertFalse(authorization.hasPermission(authentication, AdminPermission.DASHBOARD_READ));
    }

    @Test
    void exigeSessaoCookieValidadaEatribuicaoAtivaDoBanco() {
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);
        when(assignments.findActiveByUserId(44)).thenReturn(Optional.empty());
        var authentication = authentication(true, true, true);

        assertFalse(authorization.hasValidPrivilegedSession(authentication));
        assertFalse(authorization.hasPermission(authentication, AdminPermission.DASHBOARD_READ));

        when(assignments.findActiveByUserId(44)).thenReturn(Optional.of(assignment(AdminProfile.MODERATOR)));
        assertTrue(authorization.hasPermission(authentication, AdminPermission.CONTENT_DEACTIVATE));
        assertFalse(authorization.hasPermission(authentication, AdminPermission.CONTENT_REACTIVATE));
    }

    @Test
    void flagDesabilitadoPreservaPermissoesLegadasSemLiberarGestaoDePerfis() {
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, false);
        var authentication = authentication(true, true, false);

        assertTrue(authorization.hasPermission(authentication, AdminPermission.DASHBOARD_READ));
        assertFalse(authorization.hasPermission(authentication, AdminPermission.ADMIN_MANAGE));
        verifyNoInteractions(assignments);
    }

    @Test
    void telefoneVerificadoForaDoFormatoMfaNaoEConsideradoElegivelComRbacAtivo() {
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);
        Usuario user = mock(Usuario.class);
        when(user.getId()).thenReturn(44);
        when(user.isEnabled()).thenReturn(true);
        when(user.isEmailVerificado()).thenReturn(true);
        when(user.isTelefoneVerificado()).thenReturn(true);
        when(user.getEmail()).thenReturn(new Email("admin@example.invalid"));
        when(user.getTelefone()).thenReturn("not-a-brazilian-mobile");
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(user).getAuthorities();

        assertFalse(authorization.isEligibleForPrivilegedSession(user));
    }

    @Test
    void atribuicoesIndisponiveisNegamSessaoPrivilegiadaSemFallback() {
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);
        Usuario user = new Usuario();
        ReflectionTestUtils.setField(user, "id", 44);
        user.setEmail(new Email("admin@example.invalid"));
        user.setTelefone("5511912345678");
        user.setEmailVerificado(true);
        user.setTelefoneVerificado(true);
        Role adminRole = new Role();
        adminRole.setNome("ADMIN");
        user.setRoles(new java.util.HashSet<>(java.util.Set.of(adminRole)));
        when(assignments.findActiveByUserId(44))
                .thenThrow(new DataAccessResourceFailureException("RBAC table unavailable"));

        assertFalse(authorization.isEligibleForPrivilegedSession(user));
    }

    @Test
    void justificativaSanitizadaELimitadaAntesDePersistencia() {
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);

        String safe = authorization.requireReason("PA-1234 OTP=123456");

        org.junit.jupiter.api.Assertions.assertEquals("PA-1234 OTP=[REDACTED]", safe);
        assertThrows(AccessDeniedException.class, () -> authorization.requireReason(" "));
        assertThrows(AccessDeniedException.class, () -> authorization.requireReason("x".repeat(2001)));
    }

    private AdminAssignment assignment(AdminProfile profile) {
        Instant now = Instant.now();
        return new AdminAssignment(UUID.randomUUID(), 44, 44, profile,
                AdminAssignmentStatus.ACTIVE, now, 1, now, 1, null, "approved");
    }

    private UsernamePasswordAuthenticationToken authentication(
            boolean roleAdmin, boolean adminSession, boolean sessionDetails) {
        Usuario user = mock(Usuario.class);
        when(user.getId()).thenReturn(44);
        when(user.isEnabled()).thenReturn(true);
        Collection<? extends org.springframework.security.core.GrantedAuthority> userRoles = roleAdmin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USUARIO"));
        org.mockito.Mockito.doReturn(userRoles).when(user).getAuthorities();
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(roleAdmin ? "ROLE_ADMIN" : "ROLE_USUARIO"));
        if (adminSession) {
            authorities.add(new SimpleGrantedAuthority("ADMIN_SESSION"));
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        if (sessionDetails) {
            authentication.setDetails(new AdminAuthenticationDetails(UUID.randomUUID()));
        }
        return authentication;
    }
}
