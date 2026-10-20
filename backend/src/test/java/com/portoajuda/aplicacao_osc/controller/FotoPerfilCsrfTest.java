package com.portoajuda.aplicacao_osc.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.controller.auth.AuthController;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.CorsConfig;
import com.portoajuda.aplicacao_osc.segurity.JwtAuthFilter;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.segurity.SecurityConfiguration;
import com.portoajuda.aplicacao_osc.service.CodigoAutenticacaoService;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import com.portoajuda.aplicacao_osc.service.UsuarioService;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = { AuthController.class, UsuarioController.class })
@Import({ SecurityConfiguration.class, CorsConfig.class, JwtAuthFilter.class })
@TestPropertySource(properties = {
        "ALLOWED_ORIGINS=http://localhost:5500",
        "app.frontend-url=http://localhost:5500",
        "app.cookies.secure=false"
})
class FotoPerfilCsrfTest {

    private static final byte[] PNG_COM_ASSINATURA = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CodigoAutenticacaoService codigoAutenticacaoService;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private SessaoUsuarioService sessaoUsuarioService;

    @Test
    void uploadSemTokenCsrfContinuaRejeitado() throws Exception {
        mockMvc.perform(multipart("/usuario/foto-perfil")
                        .file(arquivoPng())
                        .with(user("123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadComTokenCsrfIncorretoContinuaRejeitado() throws Exception {
        TokenCsrf csrf = obterTokenCsrf();

        mockMvc.perform(multipart("/usuario/foto-perfil")
                        .file(arquivoPng())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), "token-incorreto")
                        .with(user("123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEmitidoPeloEndpointPermiteUploadAutenticado() throws Exception {
        TokenCsrf csrf = obterTokenCsrf();
        when(usuarioService.atualizarFotoPerfil(
                eq(123), any(byte[].class), eq("image/png")))
                .thenReturn("https://storage.example/foto.png");

        mockMvc.perform(multipart("/usuario/foto-perfil")
                        .file(arquivoPng())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .with(user("123")))
                .andExpect(status().isOk());

        verify(usuarioService).atualizarFotoPerfil(
                eq(123), any(byte[].class), eq("image/png"));
    }

    @Test
    void uploadComCsrfValidoContinuaExigindoAutenticacao() throws Exception {
        TokenCsrf csrf = obterTokenCsrf();

        mockMvc.perform(multipart("/usuario/foto-perfil")
                        .file(arquivoPng())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isUnauthorized());
    }

    private TokenCsrf obterTokenCsrf() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/usuario/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = resultado.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie, "GET /usuario/csrf deve emitir o cookie CSRF");
        assertTrue(!cookie.isHttpOnly(), "O cookie CSRF deve permanecer legível pelo cliente");

        AuthController.CsrfResposta csrf = objectMapper.readValue(
                resultado.getResponse().getContentAsString(),
                AuthController.CsrfResposta.class);

        assertEquals("X-XSRF-TOKEN", csrf.headerName());
        assertEquals(cookie.getValue(), csrf.token());

        return new TokenCsrf(cookie, csrf.headerName(), csrf.token());
    }

    private MockMultipartFile arquivoPng() {
        return new MockMultipartFile(
                "foto",
                "perfil.png",
                MediaType.IMAGE_PNG_VALUE,
                PNG_COM_ASSINATURA);
    }

    private record TokenCsrf(Cookie cookie, String headerName, String token) {
    }
}
