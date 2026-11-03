package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RequestNewMembroDTO(
    @NotBlank(message = "Email obrigatório")
    @Email(message = "Email inválido")
    String email,

    @NotNull(message = "Cargo obrigatório")
    CargoOsc cargo
) {}