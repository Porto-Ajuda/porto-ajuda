package com.portoajuda.aplicacao_osc.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PreferenciaCausaOngTest {
    @Test
    void preservaTaxonomiaAntigaEMapeiaCausasParaOsQuatorzeGrupos() {
        assertEquals(14, PreferenciaCausaOng.ids().size());
        assertEquals(Set.of("assistencia-social", "direitos-humanos-cidadania"),
                PreferenciaCausaOng.fromCauses(Set.of(CausaSocial.INCLUSAO_SOCIAL)));
        assertEquals(Set.of("assistencia-social"),
                PreferenciaCausaOng.fromCauses(Set.of(CausaSocial.COMBATE_POBREZA)));
        assertEquals(Set.of("cultura-esporte"), PreferenciaCausaOng.fromCauses(
                Set.of(CausaSocial.CULTURA, CausaSocial.ESPORTE, CausaSocial.CULTURA_ESPORTE)));
        assertEquals(PreferenciaCausaOng.ids(), PreferenciaCausaOng.fromCauses(
                Arrays.stream(CausaSocial.values()).collect(java.util.stream.Collectors.toSet())));
        assertEquals(8, Set.of(CausaSocial.EDUCACAO, CausaSocial.SAUDE, CausaSocial.MEIO_AMBIENTE,
                CausaSocial.ANIMAIS, CausaSocial.INCLUSAO_SOCIAL, CausaSocial.ESPORTE,
                CausaSocial.CULTURA, CausaSocial.COMBATE_POBREZA).size());
    }
}
