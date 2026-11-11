package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Size;

public record RequestCandidaturaDTO(
        @Size(max = 2000, message = "A mensagem deve ter no máximo 2.000 caracteres.")
        String mensagem) {
}
