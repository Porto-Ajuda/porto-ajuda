package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminCapabilitiesDTO;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.dto.request.AdminAuthStartRequest;
import com.portoajuda.aplicacao_osc.dto.request.AdminAuthVerifyRequest;
import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService;
import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService.VerificationResult;
import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService.VerificationOutcome;
import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService.VerificationFailure;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAuthController.class);

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
        boolean smsRequired = adminAuthenticationService.isSmsVerificationEnabled();
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "challengeId", challengeId,
                        "smsRequired", smsRequired,
                        "mensagem", "Se as credenciais forem elegíveis, os códigos serão enviados ao e-mail e ao telefone cadastrados."));
    }

    @PostMapping("/challenges/{challengeId}/resend")
    public ResponseEntity<Map<String, String>> resend(
            @PathVariable UUID challengeId,
            @RequestBody(required = false) Map<String, String> request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String channel = request == null ? null : request.get("channel");
        AdminAuthenticationService.ResendResult result = adminAuthenticationService.reenviar(
                challengeId, channel, clientIpResolver.resolve(httpRequest));
        return switch (result) {
            case ACCEPTED -> ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                    .body(Map.of("codigo", "DELIVERY_PENDING", "mensagem", "Novo código solicitado. Aguarde a confirmação do envio."));
            case DELIVERY_IN_PROGRESS -> ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                    .body(Map.of("codigo", "DELIVERY_PENDING", "mensagem", "O envio ainda está sendo confirmado. Aguarde antes de validar o código."));
            case LIMIT_REACHED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).cacheControl(CacheControl.noStore())
                    .body(Map.of("codigo", "RESEND_LIMIT", "mensagem", "Limite de reenvios atingido. Inicie um novo login."));
            case FACTOR_COMPLETE -> ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                    .body(Map.of("codigo", "FACTOR_COMPLETE", "mensagem", "Esta etapa já foi confirmada."));
            case CHALLENGE_UNAVAILABLE -> ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                    .body(Map.of("codigo", "CHALLENGE_UNAVAILABLE", "mensagem", "Esta tentativa não permite reenvio. Inicie um novo login."));
        };
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
            VerificationFailure failure = outcome.failure() == null
                    ? VerificationFailure.INVALID_CHALLENGE : outcome.failure();
            HttpStatus status = switch (failure) {
                case INVALID_CODE, INVALID_CHALLENGE -> HttpStatus.UNAUTHORIZED;
                case EXPIRED_CODE -> HttpStatus.GONE;
                case CHALLENGE_INVALIDATED, CHALLENGE_REPLACED, CODE_ALREADY_USED -> HttpStatus.CONFLICT;
                case TOO_MANY_ATTEMPTS -> HttpStatus.TOO_MANY_REQUESTS;
                case DELIVERY_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            };
            if (failure == VerificationFailure.DELIVERY_FAILED) {
                LOGGER.warn("Admin verification blocked by failed delivery; challengeId={}, code={}",
                        challengeId, failure.name());
            }
            return ResponseEntity.status(status)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("codigo", failure.name(), "mensagem", mensagemFalha(failure)));
        }
        if (result == VerificationResult.PENDING) {
            String nextFactor = outcome.nextFactor() == null ? "UNKNOWN" : outcome.nextFactor();
            String code = "DELIVERY".equals(nextFactor) ? "DELIVERY_PENDING" : "NEXT_FACTOR_REQUIRED";
            String message = switch (nextFactor) {
                case "DELIVERY" -> "A entrega está sendo confirmada. Aguarde alguns segundos e valide o mesmo código novamente.";
                case "SMS" -> "O código de e-mail foi confirmado. Informe o código SMS enviado para concluir o acesso.";
                case "EMAIL" -> "Confirme primeiro o código enviado ao e-mail cadastrado.";
                default -> "Ainda há uma etapa de verificação pendente.";
            };
            return ResponseEntity.accepted()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("codigo", code, "nextFactor", nextFactor, "mensagem", message));
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

    private String mensagemFalha(VerificationFailure failure) {
        return switch (failure) {
            case INVALID_CODE -> "O c\u00f3digo est\u00e1 incorreto. Confira e tente novamente.";
            case EXPIRED_CODE -> "Este c\u00f3digo expirou. Inicie um novo login para receber outro.";
            case CHALLENGE_INVALIDATED -> "Esta tentativa foi invalidada. Inicie um novo login.";
            case CHALLENGE_REPLACED -> "Esta tentativa foi substitu\u00edda. Use o c\u00f3digo mais recente.";
            case CODE_ALREADY_USED -> "Este c\u00f3digo j\u00e1 foi utilizado. Inicie um novo login.";
            case TOO_MANY_ATTEMPTS -> "O limite de tentativas foi atingido. Aguarde antes de iniciar novamente.";
            case DELIVERY_FAILED -> "N\u00e3o foi poss\u00edvel entregar o c\u00f3digo. Inicie um novo login.";
            case INVALID_CHALLENGE -> "Esta tentativa n\u00e3o \u00e9 v\u00e1lida. Inicie um novo login.";
        };
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
