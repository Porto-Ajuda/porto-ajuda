package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RequestPostDTO(
    @NotBlank(message = "Defina o tipo de post")
    String tipo,

    boolean ativo,

    @NotBlank(message = "Defina um título")
    String titulo,

    String imagemUrl,

    @NotBlank(message = "Defina um contéudo ao post")
    String conteudo
) {}