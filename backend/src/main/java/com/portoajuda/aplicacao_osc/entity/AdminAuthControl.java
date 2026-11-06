package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admin_auth_controls")
@Getter
@Setter
@NoArgsConstructor
public class AdminAuthControl {

    @Id
    @Column(name = "control_key", nullable = false, length = 64)
    private String controlKey;

    @Column(name = "control_type", nullable = false, length = 32)
    private String controlType;

    @Column(name = "counter", nullable = false)
    private int counter;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
