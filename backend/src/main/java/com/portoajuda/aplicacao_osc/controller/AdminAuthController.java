package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminCapabilitiesDTO;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.dto.request.AdminAuthStartRequest;
import com.portoajuda.aplicacao_osc.dto.request.AdminAuthVerifyRequest;
import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService;
import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService.VerificationResult;
import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService.VerificationOutcome;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService.AuthenticatedAdmin;
import com.portoajuda.aplicacao_osc.segurity.ClientIpResolver;
import com.portoajuda.aplicacao_osc.segurity.AdminSessionAuthFilter;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    public static final String SESSION_SERVER_TIME_HEADER = "X-PA-ADMIN-SERVER-TIME";
    public static final String SESSION_ABSOLUTE_EXPIRY_HEADER = "X-PA-ADMIN-ABSOLUTE-EXPIRES-AT";
    public static final String SESSION_IDLE_EXPIRY_HEADER = "X-PA-ADMIN-IDLE-EXPIRES-AT";

    private final AdminAuthenticationService adminAuthenticationService;
    private final ClientIpResolver clientIpResolver;
    private final AdminPrivilegedSessionService adminSessionService;
    private final AdminSessionCookie adminSessionCookie;
    private final AdminAuthorizationService adminAuthorization;

    @PreAuthorize("@adminAuthorization.hasValidPrivilegedSession(authentication)")
    @GetMapping("/session")
    public ResponseEntity<Void> sessionStatus(
            jakarta.servlet.http.HttpServletRequest request) {
        Object sessionAttribute = request.getAttribute(AdminSessionAuthFilter.AUTHENTICATED_ADMIN_ATTRIBUTE);
        if (!(sessionAttribute instanceof AuthenticatedAdmin authenticated)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        return ResponseEntity.noContent()
                .header(SESSION_SERVER_TIME_HEADER, java.time.Instant.now().toString())
                .header(SESSION_ABSOLUTE_EXPIRY_HEADER, authenticated.absoluteExpiresAt().toString())
                .header(SESSION_IDLE_EXPIRY_HEADER, authenticated.idleExpiresAt().toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PreAuthorize("@adminAuthorization.hasValidPrivilegedSession(authentication)")
    @GetMapping("/capabilities")
    public ResponseEntity<AdminCapabilitiesDTO> capabilities() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminAuthorization.capabilities());
    }

    @PostMapping("/challenges")
    public ResponseEntity<Map<String, Object>> iniciar(
            @Valid @RequestBody AdminAuthStartRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        UUID challengeId = adminAuthenticationService.iniciar(request, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "challengeId", challengeId,
                        "mensagem", "Se as credenciais forem elegíveis, os códigos serão enviados."));
    }

    @PostMapping("/challenges/{challengeId}/verify")
    public ResponseEntity<Map<String, String>> verificar(
            @PathVariable UUID challengeId,
            @Valid @RequestBody AdminAuthVerifyRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String remoteAddress = clientIpResolver.resolve(httpRequest);
        VerificationOutcome outcome = adminAuthenticationService.verificarEEmitirSessao(
                challengeId, request, remoteAddress);
        VerificationResult result = outcome.result();
        if (result == VerificationResult.REJECTED) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("mensagem", "Autenticação administrativa inválida ou expirada."));
        }
        if (result == VerificationResult.PENDING) {
            return ResponseEntity.accepted()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("mensagem", "Ainda falta confirmar o outro canal de verificação."));
        }
        AdminPrivilegedSessionService.IssuedCredential credential = outcome.credential();
        if (credential == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("mensagem", "Autenticação administrativa inválida ou expirada."));
        }
        long maxAgeSeconds = Math.max(0,
                java.time.Duration.between(java.time.Instant.now(), credential.expiresAt()).getSeconds());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, adminSessionCookie.issue(
                        credential.rawCredential(), java.time.Duration.ofSeconds(maxAgeSeconds)).toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            jakarta.servlet.http.HttpServletRequest request) {
        String rawCredential = obterCookie(request, AdminSessionCookie.NAME);
        adminSessionService.revogarPorCredencial(
                rawCredential, "LOGOUT", clientIpResolver.resolve(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, adminSessionCookie.clear().toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).SESSION_REVOKE_SELF)")
    @PostMapping("/revoke-all")
    public ResponseEntity<Void> revokeAllOwnAdminSessions(
            @AuthenticationPrincipal com.portoajuda.aplicacao_osc.entity.Usuario administrator) {
        adminSessionService.revogarTodasDoUsuario(administrator.getId(), "ADMIN_REVOKED");
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, adminSessionCookie.clear().toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private String obterCookie(jakarta.servlet.http.HttpServletRequest request, String cookieName) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
