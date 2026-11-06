package com.portoajuda.aplicacao_osc.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final String frontendUrl;

    public ConfigController(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/database")
    public Map<String, String> database() {
        return Map.of("status", "SUPABASE");
    }

    @GetMapping("/frontend")
    public ResponseEntity<Map<String, String>> frontend() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("origin", frontendOrigin()));
    }

    private String frontendOrigin() {
        try {
            URI configured = URI.create(frontendUrl == null ? "" : frontendUrl.trim());
            String scheme = configured.getScheme() == null
                    ? ""
                    : configured.getScheme().toLowerCase(Locale.ROOT);
            String host = configured.getHost();
            int port = configured.getPort();

            if (!configured.isAbsolute()
                    || host == null
                    || port == 0
                    || port > 65535
                    || configured.getRawUserInfo() != null
                    || configured.getRawQuery() != null
                    || configured.getRawFragment() != null
                    || !("https".equals(scheme)
                            || ("http".equals(scheme) && isLoopback(host)))) {
                throw new IllegalArgumentException("Origem frontend inválida.");
            }

            return scheme + "://" + configured.getRawAuthority();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A origem pública do frontend não está configurada corretamente.");
        }
    }

    private boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }
}
