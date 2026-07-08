package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.br.CNPJ;

public record RequestOscDTO(
    @NotBlank(message = "CNPJ é obrigatório")
    @CNPJ(message = "Formato inválido")
    String cnpj,

    @NotBlank(message = "Nome da OSC inválido")
    String nome,

    @NotBlank(message = "Pix inválido")
    String pix,

    @NotBlank(message = "Tipo inválido")
    String tipoPix

    //depois volto aqui


) {}