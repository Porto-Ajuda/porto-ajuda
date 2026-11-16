package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequestRevisaoVerificacaoDTO(
        @NotBlank(message = "Selecione uma decisão de revisão.")
        @Pattern(regexp = "REQUEST_INFORMATION|APPROVE|REJECT", message = "Decisão de revisão inválida.")
        String decision,
        @NotBlank(message = "A justificativa é obrigatória.")
        @Size(max = 2000, message = "A justificativa não pode ultrapassar 2.000 caracteres.")
        String justification) {
}
