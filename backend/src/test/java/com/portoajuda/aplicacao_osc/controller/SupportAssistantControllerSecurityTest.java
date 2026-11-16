package com.portoajuda.aplicacao_osc.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
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
import com.portoajuda.aplicacao_osc.support.SupportAssistantController;
import com.portoajuda.aplicacao_osc.support.SupportAssistantAdminController;
import com.portoajuda.aplicacao_osc.support.SupportAssistantService;
import com.portoajuda.aplicacao_osc.support.SupportDtos;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers={SupportAssistantController.class,SupportAssistantAdminController.class})
@Import({SecurityConfiguration.class,CorsConfig.class,JwtAuthFilter.class,AdminSessionAuthFilter.class,
        AdminSessionCookie.class,AdminAuthorizationService.class})
@TestPropertySource(properties={"ALLOWED_ORIGINS=http://localhost:8080","app.cookies.secure=false"})
class SupportAssistantControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean SupportAssistantService support;
    @MockitoBean UsuarioRepository users;
    @MockitoBean SessaoUsuarioService sessions;
    @MockitoBean JwtService jwt;
    @MockitoBean AdminPrivilegedSessionService adminSessions;
    @MockitoBean AdminAssignmentRepository assignments;
    @MockitoBean ClientIpResolver clientIp;
    private Usuario user;
    private UsernamePasswordAuthenticationToken userAuthentication;

    @BeforeEach void setup() throws Exception {
        user=new Usuario(); Field id=Usuario.class.getDeclaredField("id");id.setAccessible(true);id.set(user,70001);
        user.setNome("Synthetic Support User");
        userAuthentication=new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
    }

    @Test void introductoryHelpAndPublicSearchAreAnonymousReadOnly() throws Exception {
        when(support.intro()).thenReturn(new SupportDtos.Intro("Ajuda","Orientação pública",List.of(),"A equipe poderá ler conversas.","Sophie",false,false));
        when(support.publicSearch("ONG teste","ALL")).thenReturn(List.of());
        mvc.perform(get("/support-assistant/intro")).andExpect(status().isOk());
        mvc.perform(get("/support-assistant/public-search").queryParam("query", "ONG teste").queryParam("category", "ALL")).andExpect(status().isOk());
        verify(support).intro(); verify(support).publicSearch("ONG teste","ALL");
    }

    @Test void voicePreferenceScopeIsAuthenticatedAndContainsOnlyTheCurrentPrincipalId() throws Exception {
        mvc.perform(get("/support-assistant/voice-preference-scope")).andExpect(status().isUnauthorized());
        mvc.perform(get("/support-assistant/voice-preference-scope").with(authentication(userAuthentication)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.userId").value(70001));
        verifyNoInteractions(support);
    }

    @Test void persistentConversationRequiresAuthenticationAndCsrf() throws Exception {
        var anonymousRejected=mvc.perform(post("/support-assistant/conversations"))
                .andExpect(status().isForbidden()).andReturn();
        Cookie anonymousCsrf=anonymousRejected.getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/support-assistant/conversations").cookie(anonymousCsrf)
                        .header("X-XSRF-TOKEN",anonymousCsrf.getValue()))
                .andExpect(status().isUnauthorized());
        var rejected=mvc.perform(post("/support-assistant/conversations").with(authentication(userAuthentication)))
                .andExpect(status().isForbidden()).andReturn();
        verifyNoInteractions(support);

        Cookie csrf=rejected.getResponse().getCookie("XSRF-TOKEN");
        UUID id=UUID.randomUUID(); Instant now=Instant.now();
        when(support.create(user)).thenReturn(new SupportDtos.Conversation(id,"AI_ACTIVE",null,now,now,null,null,0,false,List.of(),List.of()));
        mvc.perform(post("/support-assistant/conversations").with(authentication(userAuthentication)).cookie(csrf)
                        .header("X-XSRF-TOKEN",csrf.getValue()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
        verify(support).create(user);

        UUID replacementId=UUID.randomUUID();
        when(support.startNewConversation(any(SupportDtos.NewConversationRequest.class),eq(user)))
                .thenReturn(new SupportDtos.Conversation(replacementId,"AI_ACTIVE",null,now,now,null,null,0,false,List.of(),List.of()));
        String body="{\"conversationId\":\""+replacementId+"\",\"previousConversationId\":\""+id+"\"}";
        mvc.perform(post("/support-assistant/conversations/new").with(authentication(userAuthentication))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/support-assistant/conversations/new").with(authentication(userAuthentication)).cookie(csrf)
                        .header("X-XSRF-TOKEN",csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        verify(support).startNewConversation(any(SupportDtos.NewConversationRequest.class),eq(user));

        when(support.continueWithSophie(id,user))
                .thenReturn(new SupportDtos.Conversation(id,"AI_ACTIVE",null,now,now,null,null,2,false,List.of(),List.of()));
        mvc.perform(post("/support-assistant/conversations/"+id+"/continue-with-sophie")
                        .with(authentication(userAuthentication)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/support-assistant/conversations/"+id+"/continue-with-sophie")
                        .with(authentication(userAuthentication)).cookie(csrf).header("X-XSRF-TOKEN",csrf.getValue()))
                .andExpect(status().isOk());
        verify(support).continueWithSophie(id,user);

        mvc.perform(post("/support-assistant/conversations/"+id+"/retry-ai")
                        .with(authentication(userAuthentication)))
                .andExpect(status().isForbidden());
        when(support.retryAi(id,user)).thenReturn(new SupportDtos.Conversation(id,"AI_ACTIVE",null,now,now,null,null,3,false,List.of(),List.of()));
        mvc.perform(post("/support-assistant/conversations/"+id+"/retry-ai")
                        .with(authentication(userAuthentication)).cookie(csrf).header("X-XSRF-TOKEN",csrf.getValue()))
                .andExpect(status().isOk());
        verify(support).retryAi(id,user);
    }

    @Test void commonUserRoleCannotOpenStaffConversationQueue() throws Exception {
        mvc.perform(get("/admin/support-assistant/conversations").with(authentication(userAuthentication)))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(support);
    }
}
