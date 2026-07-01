package com.portoajuda.aplicacao_osc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum StatusVaga {
    ABERTA,
    ENCERRADA,
    CANCELADA;

    @JsonCreator
    public static StatusVaga fromString(String valor) {
        return StatusVaga.valueOf(valor.toUpperCase());
    }
}
