package com.portoajuda.aplicacao_osc.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAssignmentProfileRequest(
        @NotNull AdminProfile profile,
        @NotBlank @Size(max = 2000) String reason) {
}
