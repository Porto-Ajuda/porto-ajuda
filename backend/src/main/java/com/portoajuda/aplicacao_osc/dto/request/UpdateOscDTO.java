package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record UpdateOscDTO(
        @NotBlank(message = "Pix inválido")
        String pix,

        @NotBlank(message = "Tipo inválido")
        String tipoPix,

        @NotBlank(message = "Necessário descrição")
        String descricao,

        @NotBlank(message = "Obrigatório definir email")
        @Email
        String email,

        String telefone,

        @URL(message = "Url inválida")
        String site
) {}