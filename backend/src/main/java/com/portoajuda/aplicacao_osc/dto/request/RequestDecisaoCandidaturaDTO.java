package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import jakarta.validation.constraints.NotNull;

public record RequestDecisaoCandidaturaDTO(
        @NotNull StatusCandidatura status) {
}
