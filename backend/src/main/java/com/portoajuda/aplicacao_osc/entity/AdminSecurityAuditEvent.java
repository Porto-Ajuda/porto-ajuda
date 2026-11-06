package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Somente metadados sanitizados; nunca armazene senha, OTP, token ou payload SMS. */
@Entity
@Table(name = "admin_security_audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AdminSecurityAuditEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "usuario_id_snapshot")
    private Integer usuarioIdSnapshot;

    @Column(name = "challenge_id")
    private UUID challengeId;

    @Column(name = "account_key_hash", length = 64)
    private String accountKeyHash;

    @Column(name = "ip_key_hash", length = 64)
    private String ipKeyHash;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
