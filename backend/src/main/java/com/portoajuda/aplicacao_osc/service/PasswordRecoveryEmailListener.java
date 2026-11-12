package com.portoajuda.aplicacao_osc.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PasswordRecoveryEmailListener {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryEmailListener.class);
    private final EmailService emailService;
    private final String frontendUrl;

    public PasswordRecoveryEmailListener(EmailService emailService,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.emailService = emailService;
        this.frontendUrl = validarFrontendUrl(frontendUrl);
    }

    @TransactionalEventListener
    @Async("passwordRecoveryExecutor")
    public void enviarAposCommit(PasswordRecoveryRequested event) {
        String token = URLEncoder.encode(event.token(), StandardCharsets.UTF_8);
        String link = frontendUrl.replaceAll("/+$", "") + "/login#redefinir-senha/" + token;
        try {
            emailService.enviarRecuperacaoSenha(event.email(), link);
        } catch (RuntimeException deliveryFailure) {
            // Do not log exception details: provider failures can contain message payloads.
            log.warn("Password recovery email dispatch failed; no credential data was logged.");
        }
    }

    private String validarFrontendUrl(String configuredUrl) {
        try {
            URI uri = new URI(configuredUrl).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().replace("[", "").replace("]", "");
            boolean loopback = host.equalsIgnoreCase("localhost")
                    || host.equals("127.0.0.1") || host.equals("::1");
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !(scheme.equals("https") || (scheme.equals("http") && loopback))) {
                throw new IllegalStateException("APP_FRONTEND_URL deve ser uma origem HTTPS confiável; HTTP só é aceito em loopback.");
            }
            return uri.toString().replaceAll("/+$", "");
        } catch (URISyntaxException invalidUrl) {
            throw new IllegalStateException("APP_FRONTEND_URL não é uma URL confiável válida.");
        }
    }
}
