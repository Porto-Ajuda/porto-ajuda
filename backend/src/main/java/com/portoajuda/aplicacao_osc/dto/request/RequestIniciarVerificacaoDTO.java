package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RequestIniciarVerificacaoDTO(
        @NotBlank(message = "Informe seu vínculo de representação.")
        @Pattern(regexp = "LEGAL_REPRESENTATIVE|AUTHORIZED_DELEGATE", message = "Vínculo de representação inválido.")
        String representationType,
        @AssertTrue(message = "Confirme a adesão voluntária sem alterar a visibilidade, o status ou as restrições operacionais existentes.")
        boolean confirmPendingAndVisibilityEffect) {
}
