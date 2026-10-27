package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.br.CNPJ;

public record UpdateOscDTO(
        @CNPJ(message = "CNPJ inválido")
        String cnpj,

        @NotBlank(message = "Descrição é obrigatória")
        String descricao,

        @NotBlank(message = "E-mail é obrigatório")
        @Email
        String email,

        String telefone,

        @URL(message = "URL inválida")
        String site
) {
}
