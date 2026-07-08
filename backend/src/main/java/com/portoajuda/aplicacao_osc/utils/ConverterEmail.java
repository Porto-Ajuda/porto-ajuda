package com.portoajuda.aplicacao_osc.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ConverterEmail implements AttributeConverter<Email, String> {
    @Override
    public String convertToDatabaseColumn(Email email) {
        return email == null ? null : email.valor();
    }

    @Override
    public Email convertToEntityAttribute(String valor) {
        return valor == null ? null : new Email(valor);
    }
}
