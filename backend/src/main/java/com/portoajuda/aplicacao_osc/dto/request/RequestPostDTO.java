package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestPostDTO(
    @NotBlank(message = "Defina o tipo de post")
    String tipo,

    boolean ativo,

    @NotBlank(message = "Defina um título")
    @Size(max = 255)
    String titulo,

    @Size(max = 1000)
    String imagemUrl,

    @NotBlank(message = "Defina um contéudo ao post")
    @Size(max = 10000)
    String conteudo
) {}
