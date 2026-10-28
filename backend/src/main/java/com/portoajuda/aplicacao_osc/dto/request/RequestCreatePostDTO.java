package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.Valid;

public record RequestCreatePostDTO(
        @Valid RequestPostDTO post,
        @Valid RequestVagaDTO vaga
) { }