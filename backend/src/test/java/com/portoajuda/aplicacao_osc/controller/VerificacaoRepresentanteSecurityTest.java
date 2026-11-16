package com.portoajuda.aplicacao_osc.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.dto.request.RequestIniciarVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestReaplicarVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.response.VerificacaoRepresentanteDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.AdminSessionAuthFilter;
import com.portoajuda.aplicacao_osc.segurity.ClientIpResolver;
import com.portoajuda.aplicacao_osc.segurity.CorsConfig;
import com.portoajuda.aplicacao_osc.segurity.JwtAuthFilter;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.segurity.SecurityConfiguration;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import com.portoajuda.aplicacao_osc.service.VerificacaoRepresentanteService;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {VerificacaoRepresentanteController.class, AdminVerificacaoRepresentanteController.class})
@Import({SecurityConfiguration.class, CorsConfig.class, JwtAuthFilter.class,
        AdminSessionAuthFilter.class, AdminSessionCookie.class, AdminAuthorizationService.class})
@TestPropertySource(properties = {"ALLOWED_ORIGINS=http://localhost:8080", "app.cookies.secure=false",
        "app.admin.rbac.enforcement-enabled=true"})
class VerificacaoRepresentanteSecurityTest {
    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean VerificacaoRepresentanteService verification;
    @MockitoBean UsuarioRepository userRepository;
    @MockitoBean SessaoUsuarioService userSessions;
    @MockitoBean JwtService jwtService;
    @MockitoBean AdminPrivilegedSessionService adminSessions;
    @MockitoBean AdminAssignmentRepository assignments;
    @MockitoBean ClientIpResolver clientIpResolver;
    private Usuario applicant;
    private UsernamePasswordAuthenticationToken userAuthentication;

    @BeforeEach void setup() throws Exception {
        applicant = new Usuario();
        Field id = Usuario.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(applicant, 921);
        userAuthentication = new UsernamePasswordAuthenticationToken(applicant, null,
                List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
    }

    @Test void applicationAndEvidenceRoutesRequireAnAuthenticatedOwner() throws Exception {
        mockMvc.perform(get("/osc/32/verificacao")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/osc/32/verificacao/documentos/8/acesso"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(verification);
    }

    @Test void publicFeatureConfigurationDoesNotReadApplicationRecords() throws Exception {
        when(verification.isEnabled()).thenReturn(false);
        mockMvc.perform(get("/osc/verificacao/config"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(false, objectMapper.readTree(
                        result.getResponse().getContentAsString()).path("enabled").asBoolean()));
        verify(verification).isEnabled();
    }

    @Test void initiatingApplicationRequiresCsrfAndUsesTheAuthenticatedUserContext() throws Exception {
        var request = new RequestIniciarVerificacaoDTO("LEGAL_REPRESENTATIVE", true);
        var expected = new VerificacaoRepresentanteDTO(75L, 32, null, "PENDING",
                "LEGAL_REPRESENTATIVE", 0, null, false, null, List.of(), true, 1, 3, false);
        when(verification.startApplication(32, request, applicant)).thenReturn(expected);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/osc/32/verificacao/solicitar").with(authentication(userAuthentication))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(verification, never()).startApplication(32, request, applicant);

        mockMvc.perform(post("/osc/32/verificacao/solicitar").with(authentication(userAuthentication)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals("PENDING", objectMapper.readTree(
                        result.getResponse().getContentAsString()).path("status").asText()));
        verify(verification).startApplication(32, request, applicant);
    }

    @Test void rejectedApplicationResubmissionRequiresCsrfAndUsesTheAuthenticatedUserContext() throws Exception {
        var request = new RequestReaplicarVerificacaoDTO("AUTHORIZED_DELEGATE", true);
        var expected = new VerificacaoRepresentanteDTO(76L, 32, null, "PENDING",
                "AUTHORIZED_DELEGATE", 0, null, false, null, List.of(), true, 2, 3, false);
        when(verification.reapplyAfterRejection(32, request, applicant)).thenReturn(expected);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/osc/32/verificacao/reaplicar").with(authentication(userAuthentication))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(verification, never()).reapplyAfterRejection(32, request, applicant);

        mockMvc.perform(post("/osc/32/verificacao/reaplicar").with(authentication(userAuthentication)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals(76, objectMapper.readTree(
                        result.getResponse().getContentAsString()).path("applicationId").asLong()));
        verify(verification).reapplyAfterRejection(32, request, applicant);
    }

    @Test void ordinaryRoleAdminWithoutIndependentPrivilegedSessionCannotReadReviewQueue() throws Exception {
        var roleAdmin = new UsernamePasswordAuthenticationToken(applicant, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mockMvc.perform(get("/admin/ngo-verifications").with(authentication(roleAdmin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/ngo-verifications/55/reabrir")
                    .with(authentication(roleAdmin)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"justification\":\"Justificativa sintética.\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verification);
    }
}
