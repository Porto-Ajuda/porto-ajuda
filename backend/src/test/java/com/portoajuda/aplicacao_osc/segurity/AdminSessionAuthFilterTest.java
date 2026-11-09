package com.portoajuda.aplicacao_osc.segurity;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.controller.AdminSessionCookie;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService.AuthenticatedAdmin;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminSessionAuthFilterTest {

    @Mock private AdminPrivilegedSessionService sessionService;
    @Mock private ClientIpResolver clientIpResolver;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cookieAdministrativoValidoMontaPrincipalComRoleAdminEAdminSession() throws Exception {
        Usuario admin = admin();
        Instant now = Instant.now();
        when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("192.0.2.30");
        when(sessionService.autenticar("opaque-admin-credential", "192.0.2.30"))
                .thenReturn(Optional.of(new AuthenticatedAdmin(
                        admin, java.util.UUID.randomUUID(), now.plusSeconds(7200), now.plusSeconds(1200))));
        AdminSessionAuthFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/admin/oscs/7/status");
        request.setServletPath("/admin/oscs/7/status");
        request.setCookies(new Cookie(AdminSessionCookie.NAME, "opaque-admin-credential"));
        AtomicReference<org.springframework.security.core.Authentication> seen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                seen.set(SecurityContextHolder.getContext().getAuthentication()));

        assertTrue(seen.get().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
        assertTrue(seen.get().getAuthorities().stream()
                .anyMatch(authority -> "ADMIN_SESSION".equals(authority.getAuthority())));
        assertNull(seen.get().getCredentials());
        AuthenticatedAdmin metadata = (AuthenticatedAdmin) request.getAttribute(
                AdminSessionAuthFilter.AUTHENTICATED_ADMIN_ATTRIBUTE);
        assertTrue(metadata.absoluteExpiresAt().isAfter(metadata.idleExpiresAt()));
    }

    @Test
    void jwtComumOuBearerNaoSatisfazSessaoAdministrativa() throws Exception {
        AdminSessionAuthFilter filter = filter();
        when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("unknown");
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/admin/oscs/7/status");
        request.setServletPath("/admin/oscs/7/status");
        request.addHeader("Authorization", "Bearer ordinary-jwt");
        request.setCookies(new Cookie("PA_ACCESS", "ordinary-jwt"));
        AtomicReference<org.springframework.security.core.Authentication> seen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                seen.set(SecurityContextHolder.getContext().getAuthentication()));

        assertTrue(seen.get() == null);
        verify(sessionService).autenticar(null, "unknown");
        verify(sessionService, never()).autenticar(anyString(), anyString());
    }

    @Test
    void filtroNaoValidaCookieForaDeAdminNemNasRotasPublicasMfa() {
        AdminSessionAuthFilter filter = filter();
        MockHttpServletRequest normal = new MockHttpServletRequest("GET", "/post/all");
        normal.setServletPath("/post/all");
        MockHttpServletRequest startMfa = new MockHttpServletRequest("POST", "/admin/auth/challenges");
        startMfa.setServletPath("/admin/auth/challenges");
        MockHttpServletRequest verifyMfa = new MockHttpServletRequest(
                "POST", "/admin/auth/challenges/550e8400-e29b-41d4-a716-446655440000/verify");
        verifyMfa.setServletPath("/admin/auth/challenges/550e8400-e29b-41d4-a716-446655440000/verify");
        MockHttpServletRequest malformedVerify = new MockHttpServletRequest(
                "POST", "/admin/auth/challenges/abc/verify");
        malformedVerify.setServletPath("/admin/auth/challenges/abc/verify");
        MockHttpServletRequest logout = new MockHttpServletRequest("POST", "/admin/auth/logout");
        logout.setServletPath("/admin/auth/logout");

        assertTrue(filter.shouldNotFilter(normal));
        assertTrue(filter.shouldNotFilter(startMfa));
        assertTrue(filter.shouldNotFilter(verifyMfa));
        assertFalse(filter.shouldNotFilter(malformedVerify));
        assertTrue(filter.shouldNotFilter(logout));
    }

    @Test
    void cookieInvalidoEhApagadoERequisicaoContinuaNaoAutenticada() throws Exception {
        when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("192.0.2.31");
        when(sessionService.autenticar("revoked-token", "192.0.2.31")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/oscs");
        request.setServletPath("/admin/oscs");
        request.setCookies(new Cookie(AdminSessionCookie.NAME, "revoked-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, (req, res) -> { });

        assertTrue(response.getHeader("Set-Cookie").toString().contains("Max-Age=0"));
        assertTrue(SecurityContextHolder.getContext().getAuthentication() == null);
    }

    private AdminSessionAuthFilter filter() {
        return new AdminSessionAuthFilter(sessionService, clientIpResolver, new AdminSessionCookie(true));
    }

    private Usuario admin() {
        Usuario user = new Usuario();
        ReflectionTestUtils.setField(user, "id", 18);
        Role role = new Role();
        role.setNome("ADMIN");
        user.setRoles(new HashSet<>(Set.of(role)));
        return user;
    }
}
