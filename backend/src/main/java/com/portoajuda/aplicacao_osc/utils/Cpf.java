package com.portoajuda.aplicacao_osc.utils;

import lombok.Getter;

public record Cpf(String valor) {

    public Cpf {
        valor = validateCpf(valor);
    }

    private static String validateCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            throw new IllegalArgumentException("CPF não pode ser vazio");
        }

        cpf = cpf.replaceAll("\\D", "");

        if (cpf.length() != 11 || cpf.matches("(\\d)\\1{10}")) {
            throw new IllegalArgumentException("Sequência inválida");
        }

        int[] nums = new int[11];
        for (int i = 0; i < 11; i++) {
            nums[i] = cpf.charAt(i) - '0';
        }

        for (int t = 9; t < 11; t++) {
            int sum = 0;
            for (int c = 0; c < t; c++) {
                sum += nums[c] * ((t + 1) - c);
            }

            int resto = (sum * 10) % 11;
            int dv = (resto == 10) ? 0 : resto;

            if (nums[t] != dv) {
                throw new IllegalArgumentException("CPF inválido");
            }
        }

        return cpf;
    }
}