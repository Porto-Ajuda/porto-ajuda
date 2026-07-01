package com.portoajuda.aplicacao_osc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum StatusDoacao {
    PENDENTE,
    PROCESSANDO,
    PAGA,
    EXPIRADA,
    CANCELADA;

    @JsonCreator
    public static StatusDoacao fromString(String valor) {
        return StatusDoacao.valueOf(valor.toUpperCase());
    }
}
