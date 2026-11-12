package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Size;
public record AdminAuthVerifyRequest(
        @Size(max = 64) String emailCode,
        @Size(max = 64) String smsCode) {
}
