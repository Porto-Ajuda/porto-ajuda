package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.AdminAuthControl;
import com.portoajuda.aplicacao_osc.repository.AdminAuthControlRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthControlsService {

    private final AdminAuthControlRepository repository;
    private final AdminAuthCodeSecurity security;

    @Transactional(readOnly = true)
    public boolean isLocked(String type, String rawSubject, Instant now) {
        String key = security.hashIdentifier("control:" + type, rawSubject);
        return repository.findById(key)
                .map(AdminAuthControl::getLockedUntil)
                .filter(until -> until != null && until.isAfter(now))
                .isPresent();
    }

    @Transactional
    public int increment(
            String type,
            String rawSubject,
            Instant now,
            Duration window,
            int threshold,
            Duration lockDuration,
            boolean resetAtWindow) {
        String key = security.hashIdentifier("control:" + type, rawSubject);
        Instant safeWindow = window == null ? Instant.EPOCH : now.minus(window);
        Instant lockedUntil = lockDuration == null ? now : now.plus(lockDuration);
        repository.increment(key, type, now, safeWindow, resetAtWindow, threshold, lockedUntil);
        return repository.findById(key).map(AdminAuthControl::getCounter).orElse(0);
    }

    @Transactional
    public void reset(String type, String rawSubject, Instant now) {
        String key = security.hashIdentifier("control:" + type, rawSubject);
        repository.reset(key, now);
    }
}
