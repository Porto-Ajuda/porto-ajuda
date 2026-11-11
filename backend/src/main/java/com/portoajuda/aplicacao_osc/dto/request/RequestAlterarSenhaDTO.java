package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestAlterarSenhaDTO(
        @NotBlank @Size(max = 128) String senhaAtual,
        @NotBlank @Size(min = 8, max = 128) String novaSenha,
        @NotBlank @Size(max = 128) String confirmarSenha,
        Boolean desconectarOutrosDispositivos) {
}
