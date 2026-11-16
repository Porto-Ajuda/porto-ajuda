package com.portoajuda.aplicacao_osc.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.portoajuda.aplicacao_osc.controller.AuthController;
import com.portoajuda.aplicacao_osc.segurity.ClientIpResolver;
import com.portoajuda.aplicacao_osc.dto.request.RequestUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import com.portoajuda.aplicacao_osc.service.UsuarioService;

class AuthControllerTest {

    @Test
    void legacySelfUpdateReturnsClearBadRequestWhenEmailChangeIsAttempted() {
        UsuarioService usuarioService = mock(UsuarioService.class);
        AuthController controller = new AuthController(
                usuarioService,
                mock(SessaoUsuarioService.class),
                mock(JwtService.class),
                mock(ClientIpResolver.class));
        Usuario authenticatedUser = new Usuario();
        RequestUsuarioDTO request = new RequestUsuarioDTO("01234567890", null, "Nome", null,
                java.time.LocalDate.of(1990, 1, 1), "trocar@example.invalid", "FEMININO",
                null, "SenhaValida1!");
        doThrow(new IllegalArgumentException("O e-mail da conta não pode ser alterado."))
                .when(usuarioService).update(request, authenticatedUser);

        var response = controller.put(request, authenticatedUser);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("O e-mail da conta não pode ser alterado.",
                ((java.util.Map<?, ?>) response.getBody()).get("mensagem"));
    }

    @Test
    void refreshInvalidoDistingueSessaoExpiradaDeFalhaTemporaria() {
        UsuarioService usuarioService = mock(UsuarioService.class);
        SessaoUsuarioService sessaoUsuarioService = mock(SessaoUsuarioService.class);
        JwtService jwtService = mock(JwtService.class);
        AuthController controller = new AuthController(
                usuarioService,
                sessaoUsuarioService,
                jwtService,
                mock(ClientIpResolver.class));

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
