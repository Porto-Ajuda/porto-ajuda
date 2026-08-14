package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestNewMembroDTO(
    @NotBlank(message = "Email obrigatório")
    @Email(message = "Email inválido")
    String email,

    String cargo
) {}