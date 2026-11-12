package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestRedefinirSenhaDTO(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(min = 8, max = 128) String novaSenha,
        @NotBlank @Size(max = 128) String confirmarSenha) {
}
