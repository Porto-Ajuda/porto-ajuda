package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RequestCreatePostDTO(
        @NotNull @Valid RequestPostDTO post,
        @Valid RequestVagaDTO vaga
) { }
