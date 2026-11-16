package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;
import java.util.List;

public record VerificacaoRepresentanteDetalheAdminDTO(
        Long applicationId,
        Integer oscId,
        String organizationName,
        String status,
        String representationType,
        int submissionVersion,
        Instant createdAt,
        Instant submittedAt,
        List<EvidenciaAdminDTO> evidence,
        AnaliseAssistidaDTO aiAnalysis,
        boolean aiAnalysisProcessingEnabled,
        boolean aiAnalysisResultsReadable) {
    public VerificacaoRepresentanteDetalheAdminDTO {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
    public record EvidenciaAdminDTO(
            Long id,
            int submissionVersion,
            Instant submissionAt,
            String type,
            String mediaType,
            Long sizeBytes,
            Instant uploadedAt,
            String disposalStatus,
            Instant disposalRequestedAt,
            Instant disposedAt,
            String temporaryUrl,
            Instant urlExpiresAt) { }

    /** Advisory output only: extracted values are deliberately excluded from the admin response. */
    public record AnaliseAssistidaDTO(
            String status,
            String conclusion,
            String provider,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            String errorCode,
            Instant completedAt,
            List<CampoAnaliseDTO> fields,
            List<DivergenciaAnaliseDTO> discrepancies,
            String identityComparison,
            String delegationEvidenceChain,
            String apparentValidity,
            java.time.LocalDate expiryDate) {
        public AnaliseAssistidaDTO {
            fields = fields == null ? List.of() : List.copyOf(fields);
            discrepancies = discrepancies == null ? List.of() : List.copyOf(discrepancies);
        }

        public AnaliseAssistidaDTO(String status, String conclusion, String provider, String model,
                Integer inputTokens, Integer outputTokens, String errorCode, Instant completedAt,
                List<CampoAnaliseDTO> fields, List<DivergenciaAnaliseDTO> discrepancies) {
            this(status, conclusion, provider, model, inputTokens, outputTokens, errorCode, completedAt,
                    fields, discrepancies, null, null, null, null);
        }
    }

    public record CampoAnaliseDTO(String field, String status, List<ReferenciaPaginaDTO> references) {
        public CampoAnaliseDTO { references = references == null ? List.of() : List.copyOf(references); }
    }

    public record DivergenciaAnaliseDTO(String code, String severity, List<ReferenciaPaginaDTO> references) {
        public DivergenciaAnaliseDTO { references = references == null ? List.of() : List.copyOf(references); }
    }

    public record ReferenciaPaginaDTO(long evidenceId, int page) { }
}
