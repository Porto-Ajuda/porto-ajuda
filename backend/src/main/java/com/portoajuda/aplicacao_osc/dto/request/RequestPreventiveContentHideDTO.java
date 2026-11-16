package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestPreventiveContentHideDTO(
        @NotBlank @Size(min = 8, max = 2000) String justificativa) { }
