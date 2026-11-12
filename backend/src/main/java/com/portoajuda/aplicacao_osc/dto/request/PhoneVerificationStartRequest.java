package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhoneVerificationStartRequest(@NotBlank @Size(max = 255) String senha) {
}
