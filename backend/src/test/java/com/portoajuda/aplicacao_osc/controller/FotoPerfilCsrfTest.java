package com.portoajuda.aplicacao_osc.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.controller.PhoneVerificationController;
import com.portoajuda.aplicacao_osc.controller.AdminDashboardController;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.dto.response.AdminDashboardActivityDTO;
import com.portoajuda.aplicacao_osc.dto.response.AdminDashboardOverviewDTO;
import com.portoajuda.aplicacao_osc.repository.PreferenciasAcessibilidadeRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.CorsConfig;
import com.portoajuda.aplicacao_osc.segurity.AdminSessionAuthFilter;
import com.portoajuda.aplicacao_osc.segurity.JwtAuthFilter;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.segurity.SecurityConfiguration;
import com.portoajuda.aplicacao_osc.service.CodigoAutenticacaoService;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import com.portoajuda.aplicacao_osc.service.UsuarioService;
import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.service.PhoneVerificationService;
import com.portoajuda.aplicacao_osc.service.AdminDashboardService;
import com.portoajuda.aplicacao_osc.segurity.ClientIpResolver;

import jakarta.servlet.http.Cookie;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = { AuthController.class, UsuarioController.class, AdminAuthController.class,
                PhoneVerificationController.class, AdminDashboardController.class })
@Import({ SecurityConfiguration.class, CorsConfig.class, JwtAuthFilter.class,
                AdminSessionAuthFilter.class, AdminSessionCookie.class })
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
        private PreferenciasAcessibilidadeRepository preferenciasAcessibilidadeRepository;

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private CodigoAutenticacaoService codigoAutenticacaoService;

        @MockitoBean
        private UsuarioService usuarioService;

        @MockitoBean
        private SessaoUsuarioService sessaoUsuarioService;

        @MockitoBean
        private AdminPrivilegedSessionService adminPrivilegedSessionService;

        @MockitoBean
        private AdminAuthenticationService adminAuthenticationService;

        @MockitoBean
        private PhoneVerificationService phoneVerificationService;

        @MockitoBean
        private AdminDashboardService adminDashboardService;

        @MockitoBean
        private ClientIpResolver clientIpResolver;

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

        @Test
        void confirmacaoValidaChegaAoControllerSemSessaoEAtualizaUsuario() throws Exception {
                Usuario usuario = mock(Usuario.class);
                when(jwtService.extractSubject("jwt-confirmacao-valido")).thenReturn("123");
                when(usuarioRepository.findById(123)).thenReturn(Optional.of(usuario));
                when(jwtService.validateEmailVerificationToken(
                                "jwt-confirmacao-valido", usuario)).thenReturn(true);
                when(codigoAutenticacaoService.criarCodigo(usuario)).thenReturn("codigo-de-login");

                mockMvc.perform(get("/usuario/confirmar-email")
                                .param("token", "jwt-confirmacao-valido"))
                                .andExpect(status().isFound())
                                .andExpect(redirectedUrl(
                                                "http://localhost:5500/pages/confirmacao-email.html"
                                                                + "?status=sucesso#codigo=codigo-de-login"));

                verify(usuario).setEmailVerificado(true);
                verify(usuarioRepository).save(usuario);
        }

        @Test
        void tokenDeConfirmacaoInvalidoNaoVerificaEmail() throws Exception {
                when(jwtService.extractSubject("jwt-invalido"))
                                .thenThrow(new IllegalArgumentException("token inválido"));

                mockMvc.perform(get("/usuario/confirmar-email")
                                .param("token", "jwt-invalido"))
                                .andExpect(status().isFound())
                                .andExpect(redirectedUrl(
                                                "http://localhost:5500/pages/confirmacao-email.html?status=erro"));

                verify(usuarioRepository, never()).save(any(Usuario.class));
                verify(codigoAutenticacaoService, never()).criarCodigo(any(Usuario.class));
        }

        @Test
        void tokenDeConfirmacaoExpiradoNaoVerificaEmail() throws Exception {
                Usuario usuario = mock(Usuario.class);
                when(jwtService.extractSubject("jwt-expirado")).thenReturn("123");
                when(usuarioRepository.findById(123)).thenReturn(Optional.of(usuario));
                when(jwtService.validateEmailVerificationToken("jwt-expirado", usuario))
                                .thenReturn(false);

                mockMvc.perform(get("/usuario/confirmar-email")
                                .param("token", "jwt-expirado"))
                                .andExpect(status().isFound())
                                .andExpect(redirectedUrl(
                                                "http://localhost:5500/pages/confirmacao-email.html?status=erro"));

                verify(usuario, never()).setEmailVerificado(true);
                verify(usuarioRepository, never()).save(any(Usuario.class));
                verify(codigoAutenticacaoService, never()).criarCodigo(any(Usuario.class));
        }

        @Test
        void rotasPrivadasContinuamExigindoAutenticacao() throws Exception {
                mockMvc.perform(get("/usuario/perfil"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void perfilRetornaStatusDoTelefoneEIndicadorDeAdminDerivadoDoBackend() throws Exception {
                Usuario conta = mock(Usuario.class);
                when(conta.getId()).thenReturn(123);
                when(conta.isEnabled()).thenReturn(true);
                org.mockito.Mockito.doReturn(java.util.List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN"))).when(conta).getAuthorities();
                when(conta.getEmail()).thenReturn(null);
                when(conta.getGenero()).thenReturn(null);
                when(conta.isTelefoneVerificado()).thenReturn(false);
                when(usuarioRepository.findById(123)).thenReturn(Optional.of(conta));

                mockMvc.perform(get("/usuario/perfil").with(autenticacao(conta, "ROLE_ADMIN")))
                                .andExpect(status().isOk())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .jsonPath("$.administrador").value(true))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .jsonPath("$.telefoneVerificado").value(false));
        }

        @Test
        void perfilDeUsuarioComumNaoDeclaraPermissaoAdministrativa() throws Exception {
                Usuario conta = mock(Usuario.class);
                when(conta.getId()).thenReturn(123);
                when(conta.isEnabled()).thenReturn(true);
                org.mockito.Mockito.doReturn(java.util.List.of(
                                new SimpleGrantedAuthority("ROLE_USUARIO"))).when(conta).getAuthorities();
                when(conta.getEmail()).thenReturn(null);
                when(conta.getGenero()).thenReturn(null);
                when(conta.isTelefoneVerificado()).thenReturn(true);
                when(usuarioRepository.findById(123)).thenReturn(Optional.of(conta));

                mockMvc.perform(get("/usuario/perfil").with(autenticacao(conta, "ROLE_USUARIO")))
                                .andExpect(status().isOk())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .jsonPath("$.administrador").value(false))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .jsonPath("$.telefoneVerificado").value(true));
        }

        @Test
        void verificacaoDeTelefoneUsaUsuarioAutenticadoEPermiteSessaoComumAdmin() throws Exception {
                TokenCsrf csrf = obterTokenCsrf();
                Usuario principal = mock(Usuario.class);
                when(principal.getId()).thenReturn(123);
                org.mockito.Mockito.doReturn(java.util.List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN"))).when(principal).getAuthorities();
                var challengeId = java.util.UUID.fromString("11111111-1111-4111-8111-111111111111");
                when(phoneVerificationService.iniciar(eq(123), eq("senha"), any())).thenReturn(challengeId);

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/usuario/telefone/verificacao?userId=999")
                                .cookie(csrf.cookie())
                                .header(csrf.headerName(), csrf.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"senha\":\"senha\"}")
                                .with(autenticacao(principal, "ROLE_ADMIN")))
                                .andExpect(status().isAccepted())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .jsonPath("$.challengeId").value(challengeId.toString()));

                verify(phoneVerificationService).iniciar(eq(123), eq("senha"), any());
        }

        @Test
        void confirmacaoDeTelefoneUsaChallengeEContaDoPrincipal() throws Exception {
                TokenCsrf csrf = obterTokenCsrf();
                Usuario principal = mock(Usuario.class);
                when(principal.getId()).thenReturn(123);
                org.mockito.Mockito.doReturn(java.util.List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN"))).when(principal).getAuthorities();
                var challengeId = java.util.UUID.fromString("22222222-2222-4222-8222-222222222222");
                when(phoneVerificationService.confirmar(eq(123), eq(challengeId), eq("123456"), any()))
                                .thenReturn(true);

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/usuario/telefone/verificacao/" + challengeId)
                                .cookie(csrf.cookie())
                                .header(csrf.headerName(), csrf.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"codigo\":\"123456\"}")
                                .with(autenticacao(principal, "ROLE_ADMIN")))
                                .andExpect(status().isNoContent());

                verify(phoneVerificationService).confirmar(eq(123), eq(challengeId), eq("123456"), any());
        }

        @Test
        void verificacaoDeTelefoneContinuaExigindoAutenticacaoComCsrf() throws Exception {
                TokenCsrf csrf = obterTokenCsrf();
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/usuario/telefone/verificacao")
                                .cookie(csrf.cookie())
                                .header(csrf.headerName(), csrf.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"senha\":\"senha\"}"))
                                .andExpect(status().isUnauthorized());

                verify(phoneVerificationService, never()).iniciar(anyInt(), any(), any());
        }

        @Test
        void administradorComSessaoComumSemCookiePrivilegiadoNaoAcessaIndicadores() throws Exception {
                mockMvc.perform(get("/admin/dashboard/overview")
                                .servletPath("/admin/dashboard/overview")
                                .with(user("123").roles("ADMIN")))
                                .andExpect(status().isUnauthorized());

                verify(adminDashboardService, never()).obterVisaoGeral();
        }

        @Test
        void indicadoresExigemCookieDeSessaoPrivilegiadaEUsamCacheNoStore() throws Exception {
                Usuario admin = new Usuario();
                org.springframework.test.util.ReflectionTestUtils.setField(admin, "id", 63);
                Role roleAdmin = new Role();
                roleAdmin.setNome("ADMIN");
                admin.setRoles(java.util.Set.of(roleAdmin));
                java.time.Instant now = java.time.Instant.now();
                when(clientIpResolver.resolve(any())).thenReturn("192.0.2.63");
                when(adminPrivilegedSessionService.autenticar(
                                eq("cookie-admin-valido"), org.mockito.ArgumentMatchers.nullable(String.class)))
                                .thenReturn(Optional.of(new AdminPrivilegedSessionService.AuthenticatedAdmin(
                                                admin, java.util.UUID.randomUUID(), now.plusSeconds(7200),
                                                now.plusSeconds(1200))));
                when(adminDashboardService.obterVisaoGeral()).thenReturn(new AdminDashboardOverviewDTO(
                                23, 4, 2, 1, 16, 7, now, true,
                                java.util.List.of(new AdminDashboardActivityDTO("Sessão administrativa iniciada", now))));

                mockMvc.perform(get("/admin/dashboard/overview")
                                .servletPath("/admin/dashboard/overview")
                                .cookie(new Cookie("PA_ADMIN_SESSION", "cookie-admin-valido")))
                                .andExpect(status().isOk())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .jsonPath("$.registeredUsers").value(23))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .jsonPath("$.recentActivity[0].description")
                                                .value("Sessão administrativa iniciada"))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

                verify(adminDashboardService).obterVisaoGeral();
                verify(adminPrivilegedSessionService).autenticar(
                                eq("cookie-admin-valido"), org.mockito.ArgumentMatchers.nullable(String.class));
        }

        @Test
        void sessionEndpointMantem204EExpoeSomenteMetadadosSegurosDeExpiracao() throws Exception {
                Usuario admin = new Usuario();
                org.springframework.test.util.ReflectionTestUtils.setField(admin, "id", 64);
                Role roleAdmin = new Role();
                roleAdmin.setNome("ADMIN");
                admin.setRoles(java.util.Set.of(roleAdmin));
                java.time.Instant now = java.time.Instant.now();
                when(clientIpResolver.resolve(any())).thenReturn("192.0.2.64");
                when(adminPrivilegedSessionService.autenticar(eq("cookie-status"), eq("192.0.2.64")))
                                .thenReturn(Optional.of(new AdminPrivilegedSessionService.AuthenticatedAdmin(
                                                admin, java.util.UUID.randomUUID(), now.plusSeconds(7200),
                                                now.plusSeconds(1200))));

                mockMvc.perform(get("/admin/auth/session")
                                .servletPath("/admin/auth/session")
                                .cookie(new Cookie("PA_ADMIN_SESSION", "cookie-status"))
                                .header("Origin", "http://localhost:5500"))
                                .andExpect(status().isNoContent())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .header().string("X-PA-ADMIN-ABSOLUTE-EXPIRES-AT",
                                                                now.plusSeconds(7200).toString()))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .header().string("Access-Control-Expose-Headers",
                                                                org.hamcrest.Matchers.containsString(
                                                                                "X-PA-ADMIN-IDLE-EXPIRES-AT")))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .header().string("Cache-Control",
                                                                org.hamcrest.Matchers.containsString("no-store")));
        }

        @Test
        void cookieAdministrativoRevogadoNaoLiberaIndicadores() throws Exception {
                when(clientIpResolver.resolve(any())).thenReturn("192.0.2.62");
                when(adminPrivilegedSessionService.autenticar("cookie-revogado", "192.0.2.62"))
                                .thenReturn(Optional.empty());

                mockMvc.perform(get("/admin/dashboard/overview")
                                .servletPath("/admin/dashboard/overview")
                                .cookie(new Cookie("PA_ADMIN_SESSION", "cookie-revogado")))
                                .andExpect(status().isUnauthorized());

                verify(adminDashboardService, never()).obterVisaoGeral();
        }

        private org.springframework.test.web.servlet.request.RequestPostProcessor autenticacao(
                        Usuario principal, String autoridade) {
                when(principal.getUsername()).thenReturn("123");
                return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(new UsernamePasswordAuthenticationToken(
                                                principal, null,
                                                java.util.List.of(new SimpleGrantedAuthority(autoridade))));
        }

        @Test
        void operacaoDeLogoutAdminTambemExigeCsrfEComTokenLimpaCookieRestrito() throws Exception {
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/admin/auth/logout"))
                                .andExpect(status().isForbidden());

                TokenCsrf csrf = obterTokenCsrf();
                MvcResult resultado = mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/admin/auth/logout")
                                        .cookie(csrf.cookie())
                                        .header(csrf.headerName(), csrf.token()))
                                .andExpect(status().isNoContent())
                                .andReturn();

                assertTrue(resultado.getResponse().getHeaders("Set-Cookie").stream()
                        .anyMatch(value -> value.contains("PA_ADMIN_SESSION=")
                                && value.contains("Path=/admin") && value.contains("Max-Age=0")));
        }

        private TokenCsrf obterTokenCsrf() throws Exception {
                MvcResult resultado = mockMvc.perform(get("/usuario/csrf"))
                                .andExpect(status().isOk())
                                .andReturn();

                Cookie cookie = resultado.getResponse().getCookie("XSRF-TOKEN");
                assertNotNull(cookie, "GET /usuario/csrf deve emitir o cookie CSRF");
                assertEquals("Lax", cookie.getAttribute("SameSite"));
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
