package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.br.CNPJ;

import java.util.Set;

public record RequestOscDTO(
    @NotBlank(message = "CNPJ é obrigatório")
    @CNPJ(message = "Formato inválido")
    String cnpj,

    Set<CausaSocial> causas,

    @NotBlank(message = "Nome da OSC inválido")
    String nome,

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
    String site,

    @NotBlank(message = "Obrigatório definir cidade")
    String cidade,

    @NotBlank(message = "Necessário definir cep")
    String cep
) {}