package com.portoajuda.aplicacao_osc.segurity;

import com.portoajuda.aplicacao_osc.controller.PageController;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PageController.class)
@Import({ SecurityConfiguration.class, JwtAuthFilter.class, PageController.class })
class PageAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SessaoUsuarioService sessaoUsuarioService;

    @Test
    void perfilServeHtmlAnonimamenteMasMantemApiPrivada() throws Exception {
        mockMvc.perform(get("/perfil"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/pages/perfil.html"));

        mockMvc.perform(get("/usuario/perfil"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paginasPublicasContinuamAcessiveis() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/pages/login.html"));
    }

    @Test
    void cadastroOscTambemEUmaPaginaPublica() throws Exception {
        mockMvc.perform(get("/cadastro-osc"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/pages/cadastro-osc.html"));
    }
}
