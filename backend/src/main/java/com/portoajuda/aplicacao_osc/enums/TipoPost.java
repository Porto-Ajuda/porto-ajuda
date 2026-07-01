package com.portoajuda.aplicacao_osc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TipoPost {
    NOTICIA,
    DOACAO,
    VOLUNTARIADO;

    @JsonCreator
    public static TipoPost fromString(String valor) {
        return TipoPost.valueOf(valor.toUpperCase());
    }
}
