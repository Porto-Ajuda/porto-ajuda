package com.portoajuda.aplicacao_osc.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CnpjTest {

    @Test
    void aceitaCnpjNumericoLegadoSemAlterarSeuValorCanonico() {
        assertEquals("11222333000181", new Cnpj("11222333000181").getCnpj());
        assertEquals("11222333000181", new Cnpj("11.222.333/0001-81").getCnpj());
    }

    @Test
    void aceitaExemploOficialDeCnpjAlfanumerico() {
        assertEquals("12ABC34501DE35", new Cnpj("12ABC34501DE35").getCnpj());
        assertEquals("12ABC34501DE35", new Cnpj("12.ABC.345/01DE-35").getCnpj());
    }

    @Test
    void rejeitaDigitosVerificadoresInvalidos() {
        assertInvalid("11222333000182");
        assertInvalid("12ABC34501DE34");
    }

    @Test
    void rejeitaLetrasMinusculas() {
        assertInvalid("12abc34501DE35");
    }

    @Test
    void rejeitaCaracteresForaDoFormato() {
        assertInvalid("12@BC34501DE35");
        assertInvalid("12 ABC 345 01DE35");
    }

    @Test
    void rejeitaComprimentosDiferentesDeQuatorzePosicoes() {
        assertInvalid("12ABC34501DE3");
        assertInvalid("12ABC34501DE350");
    }

    @Test
    void rejeitaSequenciaNumericaRepetida() {
        assertInvalid("00000000000000");
        assertInvalid("11111111111111");
    }

    @Test
    void conversorJPAContinuaLendoCadastrosNumericosLegados() {
        ConverterCnpj converter = new ConverterCnpj();

        assertEquals("11222333000181",
                converter.convertToDatabaseColumn(new Cnpj("11222333000181")));
        assertEquals("11222333000181",
                converter.convertToEntityAttribute("11222333000181").getCnpj());
    }

    @Test
    void conversorJPAArmazenaCnpjAlfanumericoComoTextoCanonico() {
        ConverterCnpj converter = new ConverterCnpj();

        assertEquals("12ABC34501DE35",
                converter.convertToDatabaseColumn(new Cnpj("12ABC34501DE35")));
        assertEquals("12ABC34501DE35",
                converter.convertToEntityAttribute("12ABC34501DE35").getCnpj());
    }

    @Test
    void isValidoAceitaNumericoEAlfanumericoMasRejeitaDvInvalido() {
        assertTrue(Cnpj.isValido("11222333000181"));
        assertTrue(Cnpj.isValido("12ABC34501DE35"));
        assertFalse(Cnpj.isValido("12ABC34501DE34"));
    }

    private void assertInvalid(String value) {
        assertFalse(Cnpj.isValido(value));
        assertThrows(IllegalArgumentException.class, () -> new Cnpj(value));
    }
}
