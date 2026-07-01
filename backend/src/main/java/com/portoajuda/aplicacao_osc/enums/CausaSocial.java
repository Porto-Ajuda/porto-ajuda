package com.portoajuda.aplicacao_osc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CausaSocial {
    EDUCACAO,
    SAUDE,
    MEIO_AMBIENTE,
    ANIMAIS,
    INCLUSAO_SOCIAL,
    ESPORTE,
    CULTURA,
    COMBATE_POBREZA;

    @JsonCreator
    public static CausaSocial fromString(String valor) {
        return CausaSocial.valueOf(valor.toUpperCase());
    }
}