package com.portoajuda.aplicacao_osc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TipoChavePix {
    CPF,
    CNPJ,
    EMAIL,
    TELEFONE,
    ALEATORIA;

    @JsonCreator
    public static TipoChavePix fromString(String valor) {
        return TipoChavePix.valueOf(valor.toUpperCase());
    }
}