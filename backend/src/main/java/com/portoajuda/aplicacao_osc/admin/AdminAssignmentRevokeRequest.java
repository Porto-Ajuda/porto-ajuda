package com.portoajuda.aplicacao_osc.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAssignmentRevokeRequest(@NotBlank @Size(max = 2000) String reason) {
}
