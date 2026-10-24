package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestCancelarExclusaoDTO(
        @NotBlank @Email String email,
        @NotBlank String senha) {
}
