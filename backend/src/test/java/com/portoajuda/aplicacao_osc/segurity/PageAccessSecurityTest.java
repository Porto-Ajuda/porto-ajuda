package com.portoajuda.aplicacao_osc.segurity;

import com.portoajuda.aplicacao_osc.controller.PageController;
import com.portoajuda.aplicacao_osc.controller.AdminSessionCookie;
import com.portoajuda.aplicacao_osc.controller.AdminDashboardController;
import com.portoajuda.aplicacao_osc.controller.PostController;
import com.portoajuda.aplicacao_osc.controller.GeocodingController;
import com.portoajuda.aplicacao_osc.controller.CandidaturaVoluntariadoController;
import com.portoajuda.aplicacao_osc.controller.UsuarioController;
import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.dto.response.DisponibilidadeCandidaturaDTO;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.PreferenciasAcessibilidadeRepository;
import com.portoajuda.aplicacao_osc.service.AdminDashboardService;
import com.portoajuda.aplicacao_osc.service.PostService;
import com.portoajuda.aplicacao_osc.service.GeocodingService;
import com.portoajuda.aplicacao_osc.service.CandidaturaVoluntariadoService;
import com.portoajuda.aplicacao_osc.service.CodigoAutenticacaoService;
import com.portoajuda.aplicacao_osc.service.RecuperacaoSenhaService;
import com.portoajuda.aplicacao_osc.service.UsuarioService;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService.AuthenticatedAdmin;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockMultipartFile;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PageController.class, properties = {
        "app.frontend-url=https://portoajuda.test",
        "app.cookies.secure=false"
})
@Import({ SecurityConfiguration.class, JwtAuthFilter.class, AdminSessionAuthFilter.class,
        PageController.class, AdminDashboardController.class, PostController.class, GeocodingController.class,
        CandidaturaVoluntariadoController.class, UsuarioController.class })
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

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private GeocodingService geocodingService;

    @MockitoBean
    private CandidaturaVoluntariadoService candidaturaVoluntariadoService;

    @MockitoBean
    private CodigoAutenticacaoService codigoAutenticacaoService;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @MockitoBean
    private PreferenciasAcessibilidadeRepository preferenciasAcessibilidadeRepository;

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

        mockMvc.perform(get("/catalogo").param("categoria", "ongs"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/pages/catalogo.html"));
    }

    @Test
    void conteudoPublicoDaOscNaoExigeSessaoMasGestaoContinuaPrivada() throws Exception {
        when(postService.listarConteudoPublicoDaOsc(7)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/post/osc/7/publicos"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        mockMvc.perform(get("/post/osc/7/publicos/9"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/post/osc/7/gestao"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void showcasesPublicosNaoExigemSessaoMasNaoAbremMutacoes() throws Exception {
        when(postService.listarPublicacoesHome(any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 6)));
        when(postService.listarVoluntariadosHome(any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 6)));
        when(postService.listarPublicacoesCatalogo(any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 24)));
        when(postService.listarVoluntariadosCatalogo(any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 24)));

        mockMvc.perform(get("/post/publicos").param("page", "0").param("size", "6"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"content\":[]}"))
                .andExpect(jsonPath("$.size").value(6))
                .andExpect(jsonPath("$.number").value(0));

        mockMvc.perform(get("/post/voluntariados").param("page", "0").param("size", "6"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"content\":[]}"))
                .andExpect(jsonPath("$.size").value(6))
                .andExpect(jsonPath("$.number").value(0));

        mockMvc.perform(get("/post/catalogo/publicacoes").param("page", "0").param("size", "24"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"content\":[]}"))
                .andExpect(jsonPath("$.size").value(24));

        mockMvc.perform(get("/post/catalogo/voluntariados").param("page", "0").param("size", "24"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"content\":[]}"))
                .andExpect(jsonPath("$.size").value(24));

        mockMvc.perform(get("/post/osc/7/gestao"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/post/publicos"))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/post/catalogo/publicacoes"))
                .andExpect(status().isForbidden());
    }

    @Test
    void geocodificacaoDeBuscaEPublicaSomenteLeitura() throws Exception {
        when(geocodingService.buscarCoordenadasBusca("Recife", "PE", null))
                .thenReturn(new Coordenadas(-8.05, -34.9));

        mockMvc.perform(get("/geocoding/location").param("cidade", "Recife").param("estado", "PE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(-8.05))
                .andExpect(jsonPath("$.longitude").value(-34.9));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/geocoding/location"))
                .andExpect(status().isForbidden());
    }

    @Test
    void endpointsDeCandidaturaExigemSessaoEProtegemOperacoesMutaveisComCsrf() throws Exception {
        mockMvc.perform(get("/usuario/candidaturas"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/osc/7/candidaturas"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/voluntariado/vagas/41/minha-candidatura"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/voluntariado/vagas/41/candidaturas")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/voluntariado/vagas/41/candidaturas")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/voluntariado/vagas/41/candidaturas")
                        .with(user("ordinary-user"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultaDeDisponibilidadeDeVagaEPublicaSomenteLeitura() throws Exception {
        when(candidaturaVoluntariadoService.disponibilidadePublica(41))
                .thenReturn(new DisponibilidadeCandidaturaDTO(true));

        mockMvc.perform(get("/voluntariado/vagas/41/disponibilidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceitaCandidaturas").value(true));
    }

    @Test
    void multipartContentMutationRetainsAuthenticationAndCsrfRequirements() throws Exception {
        MockMultipartFile data = new MockMultipartFile("dados", "dados.json",
                "application/json", "{\"post\":{\"tipo\":\"DOACAO\",\"ativo\":true,\"titulo\":\"Post\",\"imagemUrl\":null,\"conteudo\":\"Texto\"},\"vaga\":null}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMultipartFile image = new MockMultipartFile("imagem", "content.png",
                "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/post/osc/7/create-com-imagem")
                        .file(data).file(image))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/post/osc/7/create-com-imagem")
                        .file(data).file(image).with(csrf()).with(user("ordinary-user")))
                .andExpect(status().isCreated());
    }

    @Test
    void cadastroOscTambemEUmaPaginaPublica() throws Exception {
        mockMvc.perform(get("/cadastro-osc"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/pages/cadastro-osc.html"));
    }

    @Test
    void passwordManagementUsesRealSecurityChainCsrfAndSessionIdentity() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn(TEST_IP);
        String recoveryRequest = "{\"email\":\"synthetic@example.invalid\"}";
        mockMvc.perform(post("/usuario/recuperacao-senha/solicitar")
                        .contentType("application/json").content(recoveryRequest))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/usuario/recuperacao-senha/solicitar").with(csrf())
                        .contentType("application/json").content(recoveryRequest))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(
                        "Solicitação recebida. Se houver uma conta elegível, as instruções podem ser encaminhadas. Se não receber uma mensagem, tente novamente mais tarde."));
        verify(recuperacaoSenhaService).solicitar("synthetic@example.invalid", TEST_IP);

        String resetRequest = "{\"token\":\"synthetic-unused-token\",\"novaSenha\":\"NewPassword1!\","
                + "\"confirmarSenha\":\"NewPassword1!\"}";
        mockMvc.perform(post("/usuario/recuperacao-senha/redefinir")
                        .contentType("application/json").content(resetRequest))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/usuario/recuperacao-senha/redefinir").with(csrf())
                        .contentType("application/json").content(resetRequest))
                .andExpect(status().isBadRequest());
        verify(recuperacaoSenhaService).redefinir(
                "synthetic-unused-token", "NewPassword1!", "NewPassword1!", TEST_IP);

        String passwordChange = "{\"usuarioId\":48,\"sessaoId\":999,\"senhaAtual\":\"Current1!\","
                + "\"novaSenha\":\"NewPassword1!\",\"confirmarSenha\":\"NewPassword1!\","
                + "\"desconectarOutrosDispositivos\":false}";
        mockMvc.perform(post("/usuario/alterar-senha").with(user("17"))
                        .contentType("application/json").content(passwordChange))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/usuario/alterar-senha").with(csrf())
                        .contentType("application/json").content(passwordChange))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/usuario/alterar-senha").with(csrf()).with(user("17"))
                        .contentType("application/json").content(passwordChange))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/usuario/alterar-senha").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(regularUserWithSession(901L)))
                        .contentType("application/json").content(passwordChange))
                .andExpect(status().isOk());
        verify(usuarioService).changePassword("Current1!", "NewPassword1!", "NewPassword1!", 17,
                901L, false);

        String withoutCheckbox = "{\"senhaAtual\":\"Current1!\",\"novaSenha\":\"NewPassword1!\","
                + "\"confirmarSenha\":\"NewPassword1!\"}";
        mockMvc.perform(post("/usuario/alterar-senha").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(regularUserWithSession(902L)))
                        .contentType("application/json").content(withoutCheckbox))
                .andExpect(status().isOk());
        verify(usuarioService).changePassword("Current1!", "NewPassword1!", "NewPassword1!", 17,
                902L, true);
    }

    private UsernamePasswordAuthenticationToken regularUserWithSession(Long sessionId) {
        var authentication = new UsernamePasswordAuthenticationToken("17", null,
                List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        authentication.setDetails(new UserAuthenticationDetails(sessionId));
        return authentication;
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
