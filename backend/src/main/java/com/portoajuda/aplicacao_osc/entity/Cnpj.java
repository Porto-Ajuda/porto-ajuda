package com.portoajuda.aplicacao_osc.entity;

import lombok.Getter;

public class Cnpj {

    @Getter
    private String cnpj;

    public Cnpj(String cnpj) {
       this.cnpj = validateCnpj(cnpj);
    }

    private String validateCnpj(String cnpj) {

        if (cnpj == null) {
            throw new IllegalArgumentException("CNPJ não pode ser vazio");
        }

        cnpj = cnpj.replaceAll("\\D", "");

        if (cnpj.length() != 14 || cnpj.matches("(\\d)\\1{13}")) {
            throw new IllegalArgumentException("Sequência inválida");
        }

        int[] nums = new int[14];
        for (int i = 0; i < 14; i++) {
            nums[i] = cnpj.charAt(i) - '0';
        }

        int[] peso1 = {5,4,3,2,9,8,7,6,5,4,3,2};
        int[] peso2 = {6,5,4,3,2,9,8,7,6,5,4,3,2};

        int soma = 0;
        for (int i = 0; i < 12; i++) {
            soma += nums[i] * peso1[i];
        }

        int resto = soma % 11;
        int dv1 = (resto < 2) ? 0 : 11 - resto;

        if (nums[12] != dv1) {
            throw new IllegalArgumentException("CNPJ inválido");
        }

        soma = 0;
        for (int i = 0; i < 13; i++) {
            soma += nums[i] * peso2[i];
        }

        resto = soma % 11;
        int dv2 = (resto < 2) ? 0 : 11 - resto;

        if (nums[13] != dv2) {
            throw new IllegalArgumentException("CNPJ inválido");
        }

        return cnpj;
    }
}