package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RequestUpdatePostDTO(
        @NotNull Integer id,
        @NotNull @Valid RequestPostDTO post,
        @Valid RequestVagaDTO vaga
) {}
