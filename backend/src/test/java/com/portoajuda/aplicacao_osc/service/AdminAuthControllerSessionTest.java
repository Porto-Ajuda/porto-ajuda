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
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.segurity.AdminSessionAuthFilter;
import com.portoajuda.aplicacao_osc.dto.request.AdminAuthVerifyRequest;
import com.portoajuda.aplicacao_osc.dto.request.AdminAuthStartRequest;
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
    void inicioInformaSeSmsFoiExigidoPeloServidor() {
        UUID challengeId = UUID.randomUUID();
        when(clientIpResolver.resolve(any())).thenReturn("192.0.2.70");
        when(authenticationService.iniciar(any(), eq("192.0.2.70"))).thenReturn(challengeId);
        AdminAuthController controller = new AdminAuthController(
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true),
                adminAuthorization);

        var response = controller.iniciar(
                new AdminAuthStartRequest("admin@example.invalid", "synthetic-password"),
                new MockHttpServletRequest("POST", "/admin/auth/challenges"));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(challengeId, response.getBody().get("challengeId"));
        assertEquals(true, response.getBody().get("smsRequired"));
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
    }

    @Test
    void verificacaoExplicitaCodigoEstadoTentativasEntregaEReuso() {
        UUID challengeId = UUID.randomUUID();
        when(clientIpResolver.resolve(any())).thenReturn("192.0.2.74");
        when(authenticationService.verificarEEmitirSessao(eq(challengeId), any(), eq("192.0.2.74")))
                .thenReturn(
                        new AdminAuthenticationService.VerificationOutcome(
                                AdminAuthenticationService.VerificationResult.REJECTED, null,
                                AdminAuthenticationService.VerificationFailure.INVALID_CODE, null),
                        new AdminAuthenticationService.VerificationOutcome(
                                AdminAuthenticationService.VerificationResult.REJECTED, null,
                                AdminAuthenticationService.VerificationFailure.EXPIRED_CODE, null),
                        new AdminAuthenticationService.VerificationOutcome(
                                AdminAuthenticationService.VerificationResult.REJECTED, null,
                                AdminAuthenticationService.VerificationFailure.CHALLENGE_REPLACED, null),
                        new AdminAuthenticationService.VerificationOutcome(
                                AdminAuthenticationService.VerificationResult.REJECTED, null,
                                AdminAuthenticationService.VerificationFailure.CODE_ALREADY_USED, null),
                        new AdminAuthenticationService.VerificationOutcome(
                                AdminAuthenticationService.VerificationResult.REJECTED, null,
                                AdminAuthenticationService.VerificationFailure.TOO_MANY_ATTEMPTS, null),
                        new AdminAuthenticationService.VerificationOutcome(
                                AdminAuthenticationService.VerificationResult.REJECTED, null,
                                AdminAuthenticationService.VerificationFailure.DELIVERY_FAILED, null));
        AdminAuthController controller = new AdminAuthController(
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true),
                adminAuthorization);
        var request = new MockHttpServletRequest("POST", "/admin/auth/challenges/verify");
        var verifyRequest = new AdminAuthVerifyRequest("synthetic-code", null);
        var expected = java.util.List.of(
                new Object[] {HttpStatus.UNAUTHORIZED, "INVALID_CODE"},
                new Object[] {HttpStatus.GONE, "EXPIRED_CODE"},
                new Object[] {HttpStatus.CONFLICT, "CHALLENGE_REPLACED"},
                new Object[] {HttpStatus.CONFLICT, "CODE_ALREADY_USED"},
                new Object[] {HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS"},
                new Object[] {HttpStatus.SERVICE_UNAVAILABLE, "DELIVERY_FAILED"});

        for (Object[] item : expected) {
            var response = controller.verificar(challengeId, verifyRequest, request);
            assertEquals(item[0], response.getStatusCode());
            assertEquals(item[1], response.getBody().get("codigo"));
            if ("DELIVERY_FAILED".equals(item[1])) {
                assertEquals("Não foi possível entregar o código. Inicie um novo login.",
                        response.getBody().get("mensagem"));
            }
            assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        }
    }

    @Test
    void entregaEmConfirmacaoEProximoFatorSaoEstadosSeparados() {
        UUID challengeId = UUID.randomUUID();
        when(clientIpResolver.resolve(any())).thenReturn("192.0.2.75");
        when(authenticationService.verificarEEmitirSessao(eq(challengeId), any(), eq("192.0.2.75")))
                .thenReturn(
                        new AdminAuthenticationService.VerificationOutcome(
                                AdminAuthenticationService.VerificationResult.PENDING, null, null, "DELIVERY"),
                        new AdminAuthenticationService.VerificationOutcome(
                                AdminAuthenticationService.VerificationResult.PENDING, null, null, "SMS"));
        AdminAuthController controller = new AdminAuthController(
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true),
                adminAuthorization);

        var deliveryPending = controller.verificar(challengeId,
                new AdminAuthVerifyRequest("synthetic-code", null), new MockHttpServletRequest());
        assertEquals(HttpStatus.ACCEPTED, deliveryPending.getStatusCode());
        assertEquals("DELIVERY_PENDING", deliveryPending.getBody().get("codigo"));
        assertEquals("DELIVERY", deliveryPending.getBody().get("nextFactor"));
        assertTrue(deliveryPending.getBody().get("mensagem").contains("Aguarde alguns segundos"));

        var smsRequired = controller.verificar(challengeId,
                new AdminAuthVerifyRequest("synthetic-code", null), new MockHttpServletRequest());
        assertEquals(HttpStatus.ACCEPTED, smsRequired.getStatusCode());
        assertEquals("NEXT_FACTOR_REQUIRED", smsRequired.getBody().get("codigo"));
        assertEquals("SMS", smsRequired.getBody().get("nextFactor"));
        assertTrue(smsRequired.getBody().get("mensagem").contains("código de e-mail foi confirmado"));
    }

    @Test
    void consultaDaSessaoNaoRetornaDadosPessoaisEExigeSessaoAdmin() throws Exception {
        AdminAuthController controller = new AdminAuthController(
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true),
                adminAuthorization);

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
        assertEquals("@adminAuthorization.hasValidPrivilegedSession(authentication)", authorization.value());
        GetMapping mapping = AdminAuthController.class
                .getMethod("sessionStatus", jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertEquals("/session", mapping.value()[0]);
    }

    @Mock private AdminAuthenticationService authenticationService;
    @Mock private ClientIpResolver clientIpResolver;
    @Mock private AdminPrivilegedSessionService sessionService;
    @Mock private AdminAuthorizationService adminAuthorization;

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
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true),
                adminAuthorization);

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
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true),
                adminAuthorization);

        var response = controller.verificar(challengeId,
                new AdminAuthVerifyRequest("email-code", null), new MockHttpServletRequest());

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        verify(sessionService, never()).emitir(any(), any());
    }

    @Test
    void logoutRevogaSessaoEApagaCookieDoMesmoPath() {
        AdminAuthController controller = new AdminAuthController(
                authenticationService, clientIpResolver, sessionService, new AdminSessionCookie(true),
                adminAuthorization);
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
