package com.portoajuda.aplicacao_osc.utils;

import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Email(String valor) {
    private static final String REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public Email {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Email não pode ser vazio.");
        }

        valor = valor.trim().toLowerCase();
        if (!valor.matches(REGEX)) {
            throw new IllegalArgumentException("Email inválido.");
        }
    }
}