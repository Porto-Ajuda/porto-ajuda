package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;
import java.util.List;

public record VerificacaoRepresentanteDTO(
        Long applicationId,
        Integer oscId,
        String organizationName,
        String status,
        String representationType,
        int submissionVersion,
        Instant submittedAt,
        boolean canStartApplication,
        String reviewFeedback,
        List<EvidenciaDTO> evidence,
        boolean legacyExempt,
        int applicationNumber,
        int maximumApplications,
        boolean canReapply) {
    public VerificacaoRepresentanteDTO {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public record EvidenciaDTO(
            Long id,
            String type,
            String mediaType,
            Long sizeBytes,
            Instant uploadedAt,
            String disposalStatus,
            Instant disposalRequestedAt,
            Instant disposedAt) {
    }
}
