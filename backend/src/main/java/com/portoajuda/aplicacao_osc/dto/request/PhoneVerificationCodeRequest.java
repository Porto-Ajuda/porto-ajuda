package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Size;
public record PhoneVerificationCodeRequest(
        @Size(max = 64) String codigo) {
}
