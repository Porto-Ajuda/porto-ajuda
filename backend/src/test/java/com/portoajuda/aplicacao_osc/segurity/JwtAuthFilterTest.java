package com.portoajuda.aplicacao_osc.segurity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class JwtAuthFilterTest {

    @AfterEach
    void limparContextoSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filtroJwtNaoRegistraOFilterForaDaCadeiaSecurity() {
        JwtAuthFilter filter = new JwtAuthFilter(
                mock(UsuarioRepository.class),
                mock(JwtService.class),
                mock(SessaoUsuarioService.class));
        SecurityConfiguration configuration = new SecurityConfiguration(filter);

        FilterRegistrationBean<JwtAuthFilter> registration =
                configuration.jwtAuthFilterServletRegistration();

        assertFalse(registration.isEnabled());
    }

    @Test
    void ignoraAccessTokenNasRotasQueValidamOutraFormaDeAutenticacao() {
        JwtAuthFilter filter = new JwtAuthFilter(
                mock(UsuarioRepository.class),
                mock(JwtService.class),
                mock(SessaoUsuarioService.class));

        MockHttpServletRequest csrf = new MockHttpServletRequest("GET", "/usuario/csrf");
        MockHttpServletRequest refresh = new MockHttpServletRequest("POST", "/usuario/refresh");
        MockHttpServletRequest perfil = new MockHttpServletRequest("GET", "/usuario/perfil");
        csrf.setServletPath("/usuario/csrf");
        refresh.setServletPath("/usuario/refresh");
        perfil.setServletPath("/usuario/perfil");

        assertTrue(filter.shouldNotFilter(csrf));
        assertTrue(filter.shouldNotFilter(refresh));
        assertFalse(filter.shouldNotFilter(perfil));
    }

    @Test
    void erroDaCadeiaDepoisDeTokenInvalidoNaoExecutaARequisicaoDuasVezes()
            throws Exception {

        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        JwtService jwtService = mock(JwtService.class);
        SessaoUsuarioService sessaoUsuarioService = mock(SessaoUsuarioService.class);
        JwtAuthFilter filter = new JwtAuthFilter(
                usuarioRepository,
                jwtService,
                sessaoUsuarioService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("PA_ACCESS", "token-sem-sessao"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractSubject("token-sem-sessao")).thenReturn("123");
        when(jwtService.extractSessionId("token-sem-sessao")).thenReturn(null);

        AtomicInteger chamadasCadeia = new AtomicInteger();
        ServletException falhaDaAplicacao = new ServletException("falha downstream");
        FilterChain chain = (servletRequest, servletResponse) -> {
            chamadasCadeia.incrementAndGet();
            throw falhaDaAplicacao;
        };

        ServletException recebida = assertThrows(
                ServletException.class,
                () -> filter.doFilter(request, response, chain));

        assertSame(falhaDaAplicacao, recebida);
        org.junit.jupiter.api.Assertions.assertEquals(1, chamadasCadeia.get());
    }

    @Test
    void falhaDeBancoNaoVira401NemContinuaComoVisitante() throws Exception {
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        JwtService jwtService = mock(JwtService.class);
        SessaoUsuarioService sessaoUsuarioService = mock(SessaoUsuarioService.class);
        JwtAuthFilter filter = new JwtAuthFilter(
                usuarioRepository,
                jwtService,
                sessaoUsuarioService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("PA_ACCESS", "token-valido"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        Usuario usuario = mock(Usuario.class);
        DataAccessResourceFailureException falhaBanco =
                new DataAccessResourceFailureException("banco indisponível");
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractSubject("token-valido")).thenReturn("123");
        when(jwtService.extractSessionId("token-valido")).thenReturn(456L);
        when(usuarioRepository.findById(123)).thenReturn(Optional.of(usuario));
        when(usuario.isEnabled()).thenReturn(true);
        when(jwtService.validateToken("token-valido", usuario)).thenReturn(true);
        when(sessaoUsuarioService.buscarSessaoValida(456L)).thenThrow(falhaBanco);

        DataAccessResourceFailureException recebida = assertThrows(
                DataAccessResourceFailureException.class,
                () -> filter.doFilter(request, response, chain));

        assertSame(falhaBanco, recebida);
        verify(chain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(HttpServletRequest.class),
                org.mockito.ArgumentMatchers.any(HttpServletResponse.class));
    }
}
