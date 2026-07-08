package com.portoajuda.aplicacao_osc.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ConverterCpf implements AttributeConverter<Cpf, String>{

    @Override
    public String convertToDatabaseColumn(Cpf cpf) {
        return cpf == null ? null : cpf.valor();
    }

    @Override
    public Cpf convertToEntityAttribute(String valor) {
        return valor == null ? null : new Cpf(valor);
    }
}
