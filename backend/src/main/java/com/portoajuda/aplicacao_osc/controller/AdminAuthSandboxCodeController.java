package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local-only inspection of the echoed message returned by SMSMais sandbox POST /send. */
@RestController
@RequestMapping("/admin/auth/sandbox")
@ConditionalOnProperty(name = "admin-auth.sandbox-code-access-enabled", havingValue = "true")
public class AdminAuthSandboxCodeController {
    private final AdminAuthenticationService authenticationService;
    private final String smsEnvironment;

    public AdminAuthSandboxCodeController(
            AdminAuthenticationService authenticationService,
            @Value("${smsmais.environment:sandbox}") String smsEnvironment) {
        this.authenticationService = authenticationService;
        this.smsEnvironment = smsEnvironment;
    }

    @GetMapping("/challenges/{challengeId}/code")
    public ResponseEntity<Map<String, String>> getCode(
            @PathVariable UUID challengeId, HttpServletRequest request) {
        if (!"sandbox".equalsIgnoreCase(smsEnvironment)
                || !isDirectLocalRequest(request)) {
            return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        }
        String code = authenticationService.obterCodigoSandbox(challengeId);
        return code == null
                ? ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build()
                : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("smsCode", code));
    }

    private boolean isDirectLocalRequest(HttpServletRequest request) {
        if (request.getHeader("Forwarded") != null || request.getHeader("X-Forwarded-For") != null
                || request.getHeader("X-Forwarded-Host") != null || request.getHeader("X-Forwarded-Proto") != null) {
            return false;
        }
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) return false;
        } catch (Exception invalidAddress) {
            return false;
        }
        String host = request.getHeader("Host");
        if (host == null) return false;
        String normalized = host.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("localhost:8080") || normalized.equals("127.0.0.1:8080")
                || normalized.equals("[::1]:8080");
    }
}
