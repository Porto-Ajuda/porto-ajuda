package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RequestModerationPointAdjustmentDTO(
        @NotNull UUID referenceKey,
        @NotNull @Min(-10) @Max(10) Integer delta,
        @NotBlank @Size(max = 2000) String justificativa) { }
