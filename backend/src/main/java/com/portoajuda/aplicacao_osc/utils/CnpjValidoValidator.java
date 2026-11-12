package com.portoajuda.aplicacao_osc.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class CnpjValidoValidator implements ConstraintValidator<CnpjValido, String> {
    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        // Obrigatoriedade é verificada por @NotBlank nos DTOs.
        return valor == null || valor.isBlank() || Cnpj.isValido(valor);
    }
}
