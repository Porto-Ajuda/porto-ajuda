package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAuthStartRequest(
        @Email @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String senha) {
}
