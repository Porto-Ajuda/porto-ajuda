package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestModerationAppealDecisionDTO(
        @NotNull Decision decisao,
        @Min(0) @Max(3) Integer pontosRevertidos,
        @NotBlank @Size(max = 2000) String justificativa) {
    public enum Decision { REJECT, PARTIAL, ACCEPT }
}
