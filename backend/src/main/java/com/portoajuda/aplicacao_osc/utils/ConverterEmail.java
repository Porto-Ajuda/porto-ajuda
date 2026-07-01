package com.portoajuda.aplicacao_osc.utils;

import com.portoajuda.aplicacao_osc.entity.Email;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ConverterEmail implements AttributeConverter<Email, String> {
    @Override
    public String convertToDatabaseColumn(Email email) {
        return email == null ? null : email.getEmail();
    }

    @Override
    public Email convertToEntityAttribute(String valor) {
        return valor == null ? null : new Email(valor);
    }
}
