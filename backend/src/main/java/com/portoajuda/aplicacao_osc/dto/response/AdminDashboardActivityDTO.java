package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;

public record AdminDashboardActivityDTO(
        String description,
        Instant occurredAt) {
}
