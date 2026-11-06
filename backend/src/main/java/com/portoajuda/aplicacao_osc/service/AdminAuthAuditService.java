package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.AdminSecurityAuditEvent;
import com.portoajuda.aplicacao_osc.repository.AdminSecurityAuditEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthAuditService {

    private final AdminSecurityAuditEventRepository repository;
    private final AdminAuthCodeSecurity security;

    public void registrar(String event, Integer userId, UUID challengeId, String accountEmail, String ip) {
        AdminSecurityAuditEvent record = new AdminSecurityAuditEvent();
        record.setId(UUID.randomUUID());
        record.setEventType(event);
        record.setUsuarioIdSnapshot(userId);
        record.setChallengeId(challengeId);
        record.setAccountKeyHash(accountEmail == null ? null : security.accountKey(accountEmail));
        record.setIpKeyHash(ip == null || ip.isBlank() ? null : security.ipKey(ip));
        record.setOccurredAt(Instant.now());
        repository.save(record);
    }
}
