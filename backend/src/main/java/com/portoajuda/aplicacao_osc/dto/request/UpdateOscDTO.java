package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.br.CNPJ;

import java.util.Set;

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
        String site,
        CausaSocial causaPrincipal,
        Set<CausaSocial> causasSecundarias,
        Set<Integer> ods
) {
}
