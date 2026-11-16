package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;

public record VerificacaoRepresentanteFilaDTO(
        Long applicationId,
        Integer oscId,
        String organizationName,
        String status,
        String representationType,
        int submissionVersion,
        Instant submittedAt) {
}
