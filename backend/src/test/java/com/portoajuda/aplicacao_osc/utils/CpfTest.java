package com.portoajuda.aplicacao_osc.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CpfTest {
    @Test
    void storesCanonicalCpfAndPreservesLeadingZero() {
        assertEquals("01234567890", new Cpf("012.345.678-90").valor());
        assertEquals("01234567890", new Cpf("01234567890").valor());
    }

    @Test
    void rejectsInvalidCheckDigitsRepeatedDigitsAndMalformedText() {
        assertThrows(IllegalArgumentException.class, () -> new Cpf("01234567891"));
        assertThrows(IllegalArgumentException.class, () -> new Cpf("111.111.111-11"));
        assertThrows(IllegalArgumentException.class, () -> new Cpf("abc01234567890xyz"));
        assertThrows(IllegalArgumentException.class, () -> new Cpf("012-345-67890"));
        assertThrows(IllegalArgumentException.class, () -> new Cpf("   "));
    }
}
