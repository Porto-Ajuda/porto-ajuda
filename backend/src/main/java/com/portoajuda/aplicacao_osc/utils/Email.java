package com.portoajuda.aplicacao_osc.utils;

import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Email(String valor) {

    public Email {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Email inválido");
        }

        if (!valor.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Formato inválido");
        }
    }
}