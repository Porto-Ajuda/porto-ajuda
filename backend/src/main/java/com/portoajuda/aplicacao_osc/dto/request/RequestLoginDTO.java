package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestLoginDTO(

        @NotBlank(message = "Preencha o campo de Email corretamente") @Email(message = "Email inválido") String email,

        @NotBlank(message = "A senha é obrigatória") String senha,

        boolean lembrarDeMim

) {
}
