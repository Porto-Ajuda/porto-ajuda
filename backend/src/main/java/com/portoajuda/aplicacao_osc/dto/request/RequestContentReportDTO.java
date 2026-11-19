package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestContentReportDTO(
        @NotNull TipoAlvo tipo,
        @NotNull Integer alvoId,
        @NotNull Motivo motivo,
        @NotBlank @Size(max = 1000) String justificativa) {
    public enum TipoAlvo { OSC, PUBLICACAO, VOLUNTARIADO, CAMPANHA }
    public enum Motivo { MISLEADING, INAPPROPRIATE, FRAUD, PRIVACY, OTHER }
}
