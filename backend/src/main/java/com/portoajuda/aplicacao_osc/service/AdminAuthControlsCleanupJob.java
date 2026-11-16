package com.portoajuda.aplicacao_osc.service;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes only expired authentication throttle buckets; it never touches user accounts or audits. */
@Component
@RequiredArgsConstructor
public class AdminAuthControlsCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthControlsCleanupJob.class);
    private static final Duration RETENTION = Duration.ofDays(30);

    private final AdminAuthControlsService controls;

    @Scheduled(cron = "${app.admin-auth.controls-cleanup-cron:0 25 3 * * *}", zone = "America/Sao_Paulo")
    public void limparControlesExpirados() {
        Instant now = Instant.now();
        int deleted = controls.deleteExpired(now.minus(RETENTION), now);
        if (deleted > 0) {
            log.info("{} registros antigos de limitação de autenticação removidos.", deleted);
        }
    }
}
