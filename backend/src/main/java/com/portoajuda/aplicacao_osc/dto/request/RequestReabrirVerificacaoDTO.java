package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestReabrirVerificacaoDTO(
        @NotBlank(message = "A justificativa é obrigatória para reabrir a solicitação.")
        @Size(max = 2000, message = "A justificativa não pode ultrapassar 2.000 caracteres.")
        String justification) { }
