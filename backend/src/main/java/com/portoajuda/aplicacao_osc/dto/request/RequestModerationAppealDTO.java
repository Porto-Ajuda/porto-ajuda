package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RequestModerationAppealDTO(
        @NotNull @Positive Long decisionHistoryId,
        @NotBlank @Size(max = 2000) String justificativa) { }
