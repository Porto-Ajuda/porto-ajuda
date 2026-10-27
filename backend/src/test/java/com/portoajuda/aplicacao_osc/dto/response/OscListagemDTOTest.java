package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OscListagemDTOTest {

    @Test
    void expoeSomenteCamposPublicosDaListagem() throws Exception {
        Osc osc = new Osc();
        osc.setNome("OSC exemplo");
        osc.setDescricao("Descrição pública");
        osc.setCidade("Santos");
        osc.setCausas(Set.of(CausaSocial.EDUCACAO));
        osc.setOds(Set.of(4));
        osc.setAtivo(true);
        osc.setLatitude(-23.96);
        osc.setLongitude(-46.33);

        OscListagemDTO dto = OscListagemDTO.de(osc);
        String json = JsonMapper.builder().build().writeValueAsString(dto);

        assertNotSame(osc.getCausas(), dto.causas());
        assertNotSame(osc.getOds(), dto.ods());
        assertTrue(json.contains("\"nome\":\"OSC exemplo\""));
        assertTrue(json.contains("\"causas\":[\"EDUCACAO\"]"));
        assertTrue(json.contains("\"cidade\":\"Santos\""));
        assertTrue(json.contains("\"ativo\":true"));
        assertFalse(json.contains("usuario"));
        assertFalse(json.contains("senha"));
        assertFalse(json.contains("posts"));
        assertFalse(json.contains("oscMembros"));
    }
}
