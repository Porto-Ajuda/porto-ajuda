package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestAtualizarStatusOscDTO(
        @NotNull StatusOsc status,
        @Size(max = 2000) String motivo) {
}
