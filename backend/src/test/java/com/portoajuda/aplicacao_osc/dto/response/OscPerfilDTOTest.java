package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.utils.Email;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OscPerfilDTOTest {

    @Test
    void retornaOdsPersistidosEPreservaCamposDoPerfil() throws Exception {
        Osc osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 81);
        osc.setNome("Instituto Exemplo");
        osc.setDescricao("Atendimento comunitário");
        osc.setCausaPrincipal(CausaSocial.EDUCACAO);
        osc.setCausasSecundarias(Set.of(CausaSocial.SAUDE));
        osc.setOds(Set.of(4, 10, 17));
        osc.setCidade("Santos");
        osc.setEmail(new Email("contato@instituto.org.br"));
        osc.setRua("Rua Exemplo");
        osc.setAtendimentoOnline(true);

        OscPerfilDTO perfil = OscPerfilDTO.de(osc);
        String json = JsonMapper.builder().build().writeValueAsString(perfil);

        assertEquals(Set.of(4, 10, 17), perfil.ods());
        assertEquals(CausaSocial.EDUCACAO, perfil.causaPrincipal());
        assertEquals(Set.of(CausaSocial.SAUDE), perfil.causasSecundarias());
        assertEquals(Set.of(CausaSocial.EDUCACAO, CausaSocial.SAUDE), perfil.causas());
        assertEquals("Santos", perfil.cidade());
        assertEquals("contato@instituto.org.br", perfil.email());
        assertEquals("Rua Exemplo", perfil.rua());
        assertTrue(perfil.atendimentoOnline());
        assertTrue(json.contains("\"ods\""));
        assertTrue(json.contains("4"));
        assertTrue(json.contains("10"));
        assertTrue(json.contains("17"));
        assertTrue(json.contains("\"causaPrincipal\":\"EDUCACAO\""));
        assertTrue(json.contains("\"causasSecundarias\":[\"SAUDE\"]"));
        assertTrue(json.contains("\"descricao\":\"Atendimento comunitário\""));
        assertTrue(json.contains("\"email\":\"contato@instituto.org.br\""));
    }
}
