package com.portoajuda.aplicacao_osc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum StatusCandidatura {
    PENDENTE,
    APROVADA,
    RECUSADA,
    DESISTIU;

    @JsonCreator
    public static StatusCandidatura fromString(String valor) {
        return StatusCandidatura.valueOf(valor.toUpperCase());
    }
}