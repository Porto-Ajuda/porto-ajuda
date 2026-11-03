package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import jakarta.validation.constraints.NotNull;

public record RequestCargoMembroDTO(
        @NotNull(message = "Cargo obrigatório")
        CargoOsc cargo) {
}
