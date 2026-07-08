package com.portoajuda.aplicacao_osc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Genero {
    FEMININO,
    MASCULINO,
    NAO_BINARIO,
    PREFIRO_NAO_INFORMAR,
    OUTRO;

    @JsonCreator
    public static Genero fromString(String valor) {
        return Genero.valueOf(valor.toUpperCase());
    }
}
