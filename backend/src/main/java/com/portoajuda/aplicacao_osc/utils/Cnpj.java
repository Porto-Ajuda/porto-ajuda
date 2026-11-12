package com.portoajuda.aplicacao_osc.utils;

import lombok.Getter;

import java.util.regex.Pattern;

/** CNPJ é armazenado e comparado como texto, sem conversão numérica. */
public final class Cnpj {

    private static final Pattern CANONICO = Pattern.compile("^[A-Z0-9]{12}[0-9]{2}$");
    private static final Pattern FORMATADO = Pattern.compile(
            "^[A-Z0-9]{2}\\.[A-Z0-9]{3}\\.[A-Z0-9]{3}/[A-Z0-9]{4}-[0-9]{2}$");
    private static final int[] PESOS_DV1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_DV2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    @Getter
    private final String cnpj;

    public Cnpj(String valor) {
        this.cnpj = normalizarEValidar(valor);
    }

    /** Retorna o CNPJ canônico (14 caracteres maiúsculos, sem pontuação). */
    public static String normalizarEValidar(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("CNPJ não pode ser nulo.");
        }

        String canonico;
        if (CANONICO.matcher(valor).matches()) {
            canonico = valor;
        } else if (FORMATADO.matcher(valor).matches()) {
            canonico = valor.replace(".", "").replace("/", "").replace("-", "");
        } else {
            throw new IllegalArgumentException(
                    "CNPJ deve conter 12 caracteres alfanuméricos maiúsculos e dois dígitos verificadores.");
        }

        if (!temDigitosVerificadoresValidos(canonico)) {
            throw new IllegalArgumentException("CNPJ inválido.");
        }
        return canonico;
    }

    public static boolean isValido(String valor) {
        try {
            normalizarEValidar(valor);
            return true;
        } catch (IllegalArgumentException erro) {
            return false;
        }
    }

    private static boolean temDigitosVerificadoresValidos(String valor) {
        if (valor.chars().distinct().count() == 1) {
            return false;
        }

        int soma = 0;
        for (int i = 0; i < 12; i++) {
            soma += valorAsciiParaCalculo(valor.charAt(i)) * PESOS_DV1[i];
        }
        int dv1 = calcularDigito(soma);
        if (valor.charAt(12) - '0' != dv1) {
            return false;
        }

        soma = 0;
        for (int i = 0; i < 12; i++) {
            soma += valorAsciiParaCalculo(valor.charAt(i)) * PESOS_DV2[i];
        }
        soma += dv1 * PESOS_DV2[12];
        int dv2 = calcularDigito(soma);
        return valor.charAt(13) - '0' == dv2;
    }

    /** Regra RFB/SERPRO: valor do caractere ASCII menos 48. */
    private static int valorAsciiParaCalculo(char caractere) {
        return caractere - '0';
    }

    private static int calcularDigito(int soma) {
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    @Override
    public String toString() {
        return cnpj;
    }
}
