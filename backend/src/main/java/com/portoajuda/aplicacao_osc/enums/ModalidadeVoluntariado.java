package com.portoajuda.aplicacao_osc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ModalidadeVoluntariado {
    PRESENCIAL,
    REMOTO,
    HIBRIDO;

    @JsonCreator
    public static ModalidadeVoluntariado fromString(String valor) {
        return ModalidadeVoluntariado.valueOf(valor.toUpperCase());
    }
}