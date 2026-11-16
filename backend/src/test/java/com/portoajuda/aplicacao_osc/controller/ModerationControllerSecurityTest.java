package com.portoajuda.aplicacao_osc.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.controller.AdminSessionCookie;
import com.portoajuda.aplicacao_osc.dto.response.ModerationSubjectDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationRestrictionNoticeDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.AdminSessionAuthFilter;
import com.portoajuda.aplicacao_osc.segurity.ClientIpResolver;
import com.portoajuda.aplicacao_osc.segurity.CorsConfig;
import com.portoajuda.aplicacao_osc.segurity.JwtAuthFilter;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.segurity.SecurityConfiguration;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.service.ModerationPointsService;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers=ModerationController.class)
@Import({SecurityConfiguration.class,CorsConfig.class,JwtAuthFilter.class,AdminSessionAuthFilter.class,
        AdminSessionCookie.class,AdminAuthorizationService.class})
@TestPropertySource(properties={"ALLOWED_ORIGINS=http://localhost:8080","app.cookies.secure=false"})
class ModerationControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    private final ObjectMapper mapper=new ObjectMapper();
    @MockitoBean ModerationPointsService moderation;
    @MockitoBean UsuarioRepository userRepository;
    @MockitoBean SessaoUsuarioService userSessions;
    @MockitoBean JwtService jwtService;
    @MockitoBean AdminPrivilegedSessionService adminSessions;
    @MockitoBean AdminAssignmentRepository assignments;
    @MockitoBean ClientIpResolver clientIpResolver;
    private Usuario user;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach void authenticatedSyntheticUser() throws Exception {
        user=new Usuario();Field id=Usuario.class.getDeclaredField("id");id.setAccessible(true);id.set(user,824);
        user.setNome("Synthetic moderation HTTP user");
        authentication=new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
    }

    @Test void pointsEndpointUsesAuthenticatedPrincipalAndNeverAcceptsClientTargetIdentity() throws Exception {
        when(moderation.myUserSubject(user)).thenReturn(new ModerationSubjectDTO("USER",824,"Synthetic moderation HTTP user",0,false,null,false,List.of(),List.of()));
        mockMvc.perform(get("/moderation/me").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(result->assertEquals(824,mapper.readTree(result.getResponse().getContentAsString()).path("subjectId").asInt()));
        verify(moderation).myUserSubject(user);
    }

    @Test void NGORestrictionNoticeRequiresAuthenticationAndUsesTheRequestedOrganizationScope() throws Exception {
        mockMvc.perform(get("/osc/14/moderation/restriction"))
                .andExpect(status().isUnauthorized());
        when(moderation.ngoRestrictionNotice(14,user)).thenReturn(new ModerationRestrictionNoticeDTO(
                "OSC",14,"Synthetic NGO",5,true,Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-15T00:00:00Z"),false,Instant.parse("2026-11-13T00:00:00Z"),
                List.of(new ModerationRestrictionNoticeDTO.Reason(92L,"UPHELD_DECISION","Synthetic public reason."))));
        mockMvc.perform(get("/osc/14/moderation/restriction").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var json=mapper.readTree(result.getResponse().getContentAsString());
                    assertEquals(14,json.path("subjectId").asInt());
                    assertEquals(5,json.path("score").asInt());
                    assertEquals("Synthetic public reason.",json.path("reasons").get(0).path("justification").asText());
                    assertEquals(false,json.has("pointsHistory"));
                    assertEquals(false,json.has("appeals"));
                });
        verify(moderation).ngoRestrictionNotice(14,user);
    }

    @Test void appealMutationRequiresCsrfAndUsesTheAuthenticatedAccount() throws Exception {
        String body=mapper.writeValueAsString(Map.of("decisionHistoryId",91,"justificativa","Synthetic appeal body."));
        MvcResult rejected=mockMvc.perform(post("/moderation/appeals").with(authentication(authentication)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andReturn();
        verify(moderation,never()).fileAppeal(91,"Synthetic appeal body.",user);

        Cookie cookie=rejected.getResponse().getCookie("XSRF-TOKEN");
        assertEquals("XSRF-TOKEN",cookie.getName());
        when(moderation.fileAppeal(91,"Synthetic appeal body.",user)).thenReturn(1L);
        mockMvc.perform(post("/moderation/appeals").with(authentication(authentication)).cookie(cookie)
                    .header("X-XSRF-TOKEN",cookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        verify(moderation).fileAppeal(91,"Synthetic appeal body.",user);
    }

    @Test void commonRoleAdminAloneCannotOpenPrivilegedEntityManagement() throws Exception {
        var roleAdmin=new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mockMvc.perform(get("/admin/moderation/entities?category=USER").with(authentication(roleAdmin)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(moderation);
    }
}
