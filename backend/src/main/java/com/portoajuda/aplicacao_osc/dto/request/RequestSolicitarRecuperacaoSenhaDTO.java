package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestSolicitarRecuperacaoSenhaDTO(
        @NotBlank @Email @Size(max = 255) String email) {
}
