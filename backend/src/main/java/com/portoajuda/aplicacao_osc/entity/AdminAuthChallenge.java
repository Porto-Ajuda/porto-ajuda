package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admin_auth_challenges")
@Getter
@Setter
@NoArgsConstructor
public class AdminAuthChallenge {

    public enum State {
        ISSUING,
        PENDING,
        VERIFIED,
        DECOY,
        INVALIDATED,
        FAILED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "account_key_hash", nullable = false, length = 64)
    private String accountKeyHash;

    @Column(name = "ip_key_hash", length = 64)
    private String ipKeyHash;

    @Column(name = "email_snapshot_hash", length = 64)
    private String emailSnapshotHash;

    @Column(name = "phone_snapshot_hash", length = 64)
    private String phoneSnapshotHash;

    @Column(name = "email_code_hash", length = 64)
    private String emailCodeHash;

    @Column(name = "sms_code_hash", length = 64)
    private String smsCodeHash;

    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts;

    @Column(name = "sms_attempts", nullable = false)
    private int smsAttempts;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private State state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "sms_verified_at")
    private Instant smsVerifiedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "delivery_failure_at")
    private Instant deliveryFailureAt;

    public boolean isPendingAt(Instant now) {
        return state == State.PENDING && expiresAt != null && expiresAt.isAfter(now);
    }
}
