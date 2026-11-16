package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record RequestContentReportDecisionDTO(
        @NotNull Decisao decisao,
        @Min(1) @Max(3) Integer pontos,
        Boolean restringirVisibilidade,
        @NotBlank @Size(min = 8, max = 2000) String justificativa) {
        public RequestContentReportDecisionDTO {
                // Backward-compatible default for older review clients that predate the explicit visibility choice.
                if (restringirVisibilidade == null) restringirVisibilidade = false;
        }
        public enum Decisao { DISMISS, UPHOLD, RESTORE }
}
