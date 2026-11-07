package com.portoajuda.aplicacao_osc.segurity;

import com.portoajuda.aplicacao_osc.controller.PageController;
import com.portoajuda.aplicacao_osc.controller.AdminSessionCookie;
import com.portoajuda.aplicacao_osc.controller.AdminDashboardController;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.service.AdminDashboardService;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService.AuthenticatedAdmin;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PageController.class)
@Import({ SecurityConfiguration.class, JwtAuthFilter.class, AdminSessionAuthFilter.class,
        PageController.class, AdminDashboardController.class })
class PageAccessSecurityTest {

    private static final String TEST_IP = "192.0.2.80";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SessaoUsuarioService sessaoUsuarioService;

    @MockitoBean
    private AdminPrivilegedSessionService adminPrivilegedSessionService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @MockitoBean
    private AdminSessionCookie adminSessionCookie;

    @MockitoBean
    private AdminDashboardService dashboardService;

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

    @Test
    void rotaAdminSemSessaoPrivilegiadaRetorna401() throws Exception {
        stubSemSessao(null);

        mockMvc.perform(get("/admin").servletPath("/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleAdminDaSessaoComumNaoAutorizaRotaAdmin() throws Exception {
        stubSemSessao(null);

        mockMvc.perform(get("/admin").servletPath("/admin")
                        .with(user("admin-comum").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cookiePrivilegiadoValidoAutorizaForwardDoPainel() throws Exception {
        stubSessaoValida("credencial-admin-valida");

        mockMvc.perform(get("/admin").servletPath("/admin").cookie(
                        new Cookie(AdminSessionCookie.NAME, "credencial-admin-valida")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/index.html"));
    }

    @Test
    void acessoDiretoAoHtmlRetorna401SemSessaoECarregaPainelComSessaoValida() throws Exception {
        stubSemSessao(null);
        mockMvc.perform(get("/admin/index.html").servletPath("/admin/index.html"))
                .andExpect(status().isUnauthorized());

        stubSessaoValida("credencial-admin-valida");
        mockMvc.perform(get("/admin/index.html").servletPath("/admin/index.html").cookie(
                        new Cookie(AdminSessionCookie.NAME, "credencial-admin-valida")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("id=\"titulo-painel-admin\"")));
    }

    @Test
    void sessaoExpiradaOuRevogadaNaoAutorizaHtmlDireto() throws Exception {
        stubSemSessao("credencial-revogada");
        when(adminSessionCookie.clear()).thenReturn(ResponseCookie.from(
                AdminSessionCookie.NAME, "").path(AdminSessionCookie.PATH)
                .maxAge(Duration.ZERO).build());

        mockMvc.perform(get("/admin/index.html").servletPath("/admin/index.html").cookie(
                        new Cookie(AdminSessionCookie.NAME, "credencial-revogada")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void assetsDoPainelContinuamPublicosEEndpointDeIndicadoresExigeSessao() throws Exception {
        mockMvc.perform(get("/styles/admin.css")).andExpect(status().isOk());
        mockMvc.perform(get("/styles/acesso-admin.css")).andExpect(status().isOk());
        mockMvc.perform(get("/components/admin.js")).andExpect(status().isOk());
        mockMvc.perform(get("/components/acesso-admin.js")).andExpect(status().isOk());
        mockMvc.perform(get("/components/modoBack.js")).andExpect(status().isOk());

        stubSemSessao(null);
        mockMvc.perform(get("/admin/dashboard/overview")
                        .servletPath("/admin/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }

    private void stubSemSessao(String credential) {
        when(clientIpResolver.resolve(any())).thenReturn(TEST_IP);
        when(adminPrivilegedSessionService.autenticar(credential, TEST_IP))
                .thenReturn(Optional.empty());
    }

    private void stubSessaoValida(String credential) {
        when(clientIpResolver.resolve(any())).thenReturn(TEST_IP);
        Instant now = Instant.now();
        Usuario admin = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(admin, "id", 25);
        Role role = new Role();
        role.setNome("ADMIN");
        admin.setRoles(new HashSet<>(Set.of(role)));
        when(adminPrivilegedSessionService.autenticar(credential, TEST_IP))
                .thenReturn(Optional.of(new AuthenticatedAdmin(
                        admin, UUID.randomUUID(), now.plus(Duration.ofHours(2)),
                        now.plus(Duration.ofMinutes(20)))));
    }
}
