package com.portoajuda.aplicacao_osc.utils;

import com.portoajuda.aplicacao_osc.entity.Cnpj;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ConverterCnpj implements AttributeConverter<Cnpj, String> {

    @Override
    public String convertToDatabaseColumn(Cnpj cnpj) {
        return cnpj == null ? null : cnpj.getCnpj();
    }

    @Override
    public Cnpj convertToEntityAttribute(String valor) {
        return valor == null ? null : new Cnpj(valor);
    }
}
