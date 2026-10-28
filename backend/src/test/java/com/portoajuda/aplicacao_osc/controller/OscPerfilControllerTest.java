package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.response.OscPerfilDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.service.OscService;
import com.portoajuda.aplicacao_osc.service.ValidacaoCnpjOscService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OscPerfilControllerTest {

    @Test
    void getPerfilRetornaCausasSeparadasEodsDoServico() throws Exception {
        OscService service = mock(OscService.class);
        OscController controller = new OscController(
                service,
                mock(ValidacaoCnpjOscService.class));

        Osc osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 81);
        osc.setNome("Instituto Exemplo");
        osc.setCausaPrincipal(CausaSocial.EDUCACAO);
        osc.setCausasSecundarias(Set.of(CausaSocial.SAUDE));
        osc.setOds(Set.of(3, 4, 17));
        OscPerfilDTO perfil = OscPerfilDTO.de(osc);

        when(service.buscarPerfilPublico(81)).thenReturn(perfil);

        ResponseEntity<OscPerfilDTO> resposta = controller.buscarPerfil(81);

        assertEquals(200, resposta.getStatusCode().value());
        assertSame(perfil, resposta.getBody());
        assertEquals(CausaSocial.EDUCACAO, resposta.getBody().causaPrincipal());
        assertEquals(Set.of(CausaSocial.SAUDE), resposta.getBody().causasSecundarias());
        assertEquals(Set.of(3, 4, 17), resposta.getBody().ods());
        assertEquals("/{id}", OscController.class
                .getMethod("buscarPerfil", Integer.class)
                .getAnnotation(GetMapping.class)
                .value()[0]);
        verify(service).buscarPerfilPublico(81);
    }
}
