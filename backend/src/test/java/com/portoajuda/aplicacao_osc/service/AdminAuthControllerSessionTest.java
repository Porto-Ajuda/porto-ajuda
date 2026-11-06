package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.controller.AdminAuthController;
import com.portoajuda.aplicacao_osc.controller.AdminSessionCookie;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.segurity.AdminSessionAuthFilter;
import com.portoajuda.aplicacao_osc.dto.request.AdminAuthVerifyRequest;
import com.portoajuda.aplicacao_osc.segurity.ClientIpResolver;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

@ExtendWith(MockitoExtension.class)
class AdminAuthControllerSessionTest {

    @Test
    void consultaDaSessaoNaoRetornaDadosPessoaisEExigeSessaoAdmin() throws Exception {
        AdminAuthController controller = new AdminAuthController(
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true));

        Instant serverTime = Instant.parse("2026-11-06T12:00:00Z");
        Instant absoluteExpiry = serverTime.plusSeconds(7200);
        Instant idleExpiry = serverTime.plusSeconds(1200);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/auth/session");
        request.setAttribute(AdminSessionAuthFilter.AUTHENTICATED_ADMIN_ATTRIBUTE,
                new AdminPrivilegedSessionService.AuthenticatedAdmin(
                        new Usuario(), UUID.randomUUID(), absoluteExpiry, idleExpiry));

        var response = controller.sessionStatus(request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        assertNotNull(response.getHeaders().getFirst(AdminAuthController.SESSION_SERVER_TIME_HEADER));
        assertEquals(absoluteExpiry.toString(), response.getHeaders().getFirst(
                AdminAuthController.SESSION_ABSOLUTE_EXPIRY_HEADER));
        assertEquals(idleExpiry.toString(), response.getHeaders().getFirst(
                AdminAuthController.SESSION_IDLE_EXPIRY_HEADER));
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        PreAuthorize authorization = AdminAuthController.class
                .getMethod("sessionStatus", jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("hasRole('ADMIN') and hasAuthority('ADMIN_SESSION')", authorization.value());
        GetMapping mapping = AdminAuthController.class
                .getMethod("sessionStatus", jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertEquals("/session", mapping.value()[0]);
    }

    @Mock private AdminAuthenticationService authenticationService;
    @Mock private ClientIpResolver clientIpResolver;
    @Mock private AdminPrivilegedSessionService sessionService;

    @Test
    void verificacaoCompletaEntregaCredencialSomenteNoHttpOnlyCookie() {
        UUID challengeId = UUID.randomUUID();
        String rawCredential = "only-in-cookie-opaque-secret";
        when(clientIpResolver.resolve(any())).thenReturn("192.0.2.71");
        when(authenticationService.verificarEEmitirSessao(eq(challengeId), any(), eq("192.0.2.71")))
                .thenReturn(new AdminAuthenticationService.VerificationOutcome(
                        AdminAuthenticationService.VerificationResult.VERIFIED,
                        new AdminPrivilegedSessionService.IssuedCredential(
                                rawCredential, Instant.now().plusSeconds(7100))));
        AdminAuthController controller = new AdminAuthController(
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true));

        var response = controller.verificar(challengeId,
                new AdminAuthVerifyRequest("email-code", "sms-code"), new MockHttpServletRequest());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains("PA_ADMIN_SESSION=" + rawCredential));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("Path=/admin"));
        assertFalseSerializedCredential(response.getBody(), rawCredential);
    }

    @Test
    void validacaoParcialNaoEmiteCookie() {
        UUID challengeId = UUID.randomUUID();
        when(clientIpResolver.resolve(any())).thenReturn("192.0.2.72");
        when(authenticationService.verificarEEmitirSessao(eq(challengeId), any(), any()))
                .thenReturn(new AdminAuthenticationService.VerificationOutcome(
                        AdminAuthenticationService.VerificationResult.PENDING, null));
        AdminAuthController controller = new AdminAuthController(
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true));

        var response = controller.verificar(challengeId,
                new AdminAuthVerifyRequest("email-code", null), new MockHttpServletRequest());

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        verify(sessionService, never()).emitir(any(), any());
    }

    @Test
    void logoutRevogaSessaoEApagaCookieDoMesmoPath() {
        AdminAuthController controller = new AdminAuthController(
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/auth/logout");
        request.setCookies(new jakarta.servlet.http.Cookie(AdminSessionCookie.NAME, "credential-to-revoke"));
        when(clientIpResolver.resolve(request)).thenReturn("192.0.2.73");

        var response = controller.logout(request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(sessionService).revogarPorCredencial("credential-to-revoke", "LOGOUT", "192.0.2.73");
        assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Path=/admin"));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
    }

    private void assertFalseSerializedCredential(Object responseBody, String credential) {
        if (responseBody != null) {
            org.junit.jupiter.api.Assertions.assertFalse(responseBody.toString().contains(credential));
        }
    }
}
