package com.portoajuda.aplicacao_osc.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.portoajuda.aplicacao_osc.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import com.portoajuda.aplicacao_osc.service.UsuarioService;

class AuthControllerTest {

    @Test
    void refreshInvalidoDistingueSessaoExpiradaDeFalhaTemporaria() {
        UsuarioService usuarioService = mock(UsuarioService.class);
        SessaoUsuarioService sessaoUsuarioService = mock(SessaoUsuarioService.class);
        JwtService jwtService = mock(JwtService.class);
        AuthController controller = new AuthController(
                usuarioService,
                sessaoUsuarioService,
                jwtService);

        when(sessaoUsuarioService.renovarSessao(null)).thenReturn(Optional.empty());

        var resposta = controller.refresh(new MockHttpServletRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, resposta.getStatusCode());
        AuthController.RefreshErro erro = assertInstanceOf(
                AuthController.RefreshErro.class,
                resposta.getBody());
        assertEquals("SESSAO_INVALIDA", erro.codigo());
        assertEquals(2, resposta.getHeaders().get(HttpHeaders.SET_COOKIE).size());
    }
}
