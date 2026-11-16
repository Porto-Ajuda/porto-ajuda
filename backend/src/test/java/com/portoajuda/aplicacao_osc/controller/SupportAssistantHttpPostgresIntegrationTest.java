package com.portoajuda.aplicacao_osc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portoajuda.aplicacao_osc.admin.AdminAssignment;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentStatus;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.admin.AdminProfile;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.AdminSessionAuthFilter;
import com.portoajuda.aplicacao_osc.segurity.ClientIpResolver;
import com.portoajuda.aplicacao_osc.segurity.CorsConfig;
import com.portoajuda.aplicacao_osc.segurity.JwtAuthFilter;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.segurity.SecurityConfiguration;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.service.GeminiRequestBudget;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import com.portoajuda.aplicacao_osc.support.SupportAssistantController;
import com.portoajuda.aplicacao_osc.support.SupportAssistantAdminController;
import com.portoajuda.aplicacao_osc.support.SupportAssistantProvider;
import com.portoajuda.aplicacao_osc.support.SupportAssistantService;
import com.portoajuda.aplicacao_osc.support.SupportConversationRepository;
import com.portoajuda.aplicacao_osc.support.SupportPublicContentSearch;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.web.filter.CharacterEncodingFilter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real controller/service/repository flow on task-owned loopback PostgreSQL, through the Spring Security chain. */
@WebMvcTest(controllers = {SupportAssistantController.class, SupportAssistantAdminController.class})
@Import({SecurityConfiguration.class, CorsConfig.class, JwtAuthFilter.class, AdminSessionAuthFilter.class,
        AdminSessionCookie.class, AdminAuthorizationService.class, AdminAssignmentRepository.class,
        SupportAssistantController.class, SupportAssistantAdminController.class,
        SupportAssistantHttpPostgresIntegrationTest.TestBeans.class})
@TestPropertySource(properties = {"ALLOWED_ORIGINS=http://localhost:8080", "app.cookies.secure=false",
        "app.support-assistant.enabled=true", "app.support-assistant.ai.enabled=false",
        "app.admin.rbac.enforcement-enabled=true"})
class SupportAssistantHttpPostgresIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean DataSource supportDataSource() {
            String url = System.getenv("SUPPORT_ASSISTANT_HTTP_TEST_JDBC_URL");
            String user = System.getenv("SUPPORT_ASSISTANT_HTTP_TEST_DB_USER");
            String password = System.getenv("SUPPORT_ASSISTANT_HTTP_TEST_DB_PASSWORD");
            if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55[0-9]{3}/porto_support_review3(?:\\?sslmode=disable)?")
                    || user == null || password == null) throw new IllegalStateException("Explicit local support test database is required.");
            var dataSource = new DriverManagerDataSource();
            dataSource.setUrl(url); dataSource.setUsername(user); dataSource.setPassword(password);
            return dataSource;
        }
        @Bean JdbcTemplate supportJdbc(DataSource supportDataSource) { return new JdbcTemplate(supportDataSource); }
        @Bean PlatformTransactionManager supportTx(DataSource supportDataSource) { return new DataSourceTransactionManager(supportDataSource); }
        @Bean SupportConversationRepository supportRepository(JdbcTemplate supportJdbc) { return new SupportConversationRepository(supportJdbc); }
        @Bean SupportPublicContentSearch publicSearch() { return mock(SupportPublicContentSearch.class); }
        @Bean GeminiRequestBudget geminiBudget() { return mock(GeminiRequestBudget.class); }
        @Bean SupportAssistantService supportService(SupportConversationRepository repository, SupportPublicContentSearch search,
                AdminAuthorizationService authorization, AdminAssignmentRepository assignments,
                GeminiRequestBudget budget, PlatformTransactionManager tx) {
            ObjectProvider<SupportAssistantProvider> providers = new StaticListableBeanFactory().getBeanProvider(SupportAssistantProvider.class);
            return new SupportAssistantService(repository, search, authorization, assignments, providers,
                    budget, tx, true, false, 12);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean UsuarioRepository users;
    @MockitoBean SessaoUsuarioService sessions;
    @MockitoBean JwtService jwt;
    @MockitoBean AdminPrivilegedSessionService adminSessions;
    @Autowired AdminAssignmentRepository assignments;
    @Autowired AdminAuthorizationService authorization;
    @MockitoBean ClientIpResolver clientIp;
    private Usuario user;
    private Usuario staff;
    private UsernamePasswordAuthenticationToken userAuth;
    private UUID conversationId;
    private int eligibleRecipientId;
    private int auditorRecipientId;
    private int inactiveRecipientId;

    @BeforeEach void setup() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("SUPPORT_ASSISTANT_TEST_CONFIRMED")),
                "Explicit disposable PostgreSQL confirmation is required.");
        int base = 930_000_000 + (int)(Math.random() * 50_000_000);
        user = principal(base); staff = principal(base + 1);
        eligibleRecipientId = base + 2; auditorRecipientId = base + 3; inactiveRecipientId = base + 4;
        Role adminRole=new Role(); adminRole.setNome("ADMIN"); staff.setRoles(java.util.Set.of(adminRole));
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS id UUID");
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS usuario_id_snapshot INTEGER");
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS profile VARCHAR(32)");
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS status VARCHAR(24)");
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS granted_at TIMESTAMPTZ");
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS granted_by_user_id_snapshot INTEGER");
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS last_modified_at TIMESTAMPTZ");
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS last_modified_by_user_id_snapshot INTEGER");
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ");
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS reason VARCHAR(2000)");
        jdbc.execute("ALTER TABLE public.admin_assignments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0");
        ensureTestAdminDirectorySchema();
        jdbc.update("INSERT INTO public.usuarios(id) VALUES (?), (?), (?), (?), (?)", base, base + 1, eligibleRecipientId, auditorRecipientId, inactiveRecipientId);
        jdbc.update("UPDATE public.usuarios SET nome=? WHERE id=?", "Atendente Sintetica", eligibleRecipientId);
        jdbc.update("UPDATE public.usuarios SET nome=? WHERE id=?", "Auditor Sintetico", auditorRecipientId);
        jdbc.update("UPDATE public.usuarios SET nome=?,ativo=FALSE WHERE id=?", "Atendente Inativo", inactiveRecipientId);
        Integer adminRoleId = jdbc.queryForObject("SELECT id FROM public.roles WHERE nome='ADMIN' ORDER BY id LIMIT 1", Integer.class);
        jdbc.update("INSERT INTO public.usuario_roles(id_usuario,id_role) VALUES (?,?),(?,?),(?,?),(?,?) ON CONFLICT DO NOTHING",
                base + 1, adminRoleId, eligibleRecipientId, adminRoleId, auditorRecipientId, adminRoleId, inactiveRecipientId, adminRoleId);
        assignments.insert(new AdminAssignment(UUID.randomUUID(), base + 1, base + 1, AdminProfile.MODERATOR,
                AdminAssignmentStatus.ACTIVE, Instant.now(), null, Instant.now(), null, null, "synthetic support integration"));
        assignments.insert(new AdminAssignment(UUID.randomUUID(), eligibleRecipientId, eligibleRecipientId, AdminProfile.MODERATOR,
                AdminAssignmentStatus.ACTIVE, Instant.now(), null, Instant.now(), null, null, "synthetic support recipient"));
        assignments.insert(new AdminAssignment(UUID.randomUUID(), auditorRecipientId, auditorRecipientId, AdminProfile.AUDITOR,
                AdminAssignmentStatus.ACTIVE, Instant.now(), null, Instant.now(), null, null, "synthetic auditor exclusion"));
        assignments.insert(new AdminAssignment(UUID.randomUUID(), inactiveRecipientId, inactiveRecipientId, AdminProfile.MODERATOR,
                AdminAssignmentStatus.ACTIVE, Instant.now(), null, Instant.now(), null, null, "synthetic inactive recipient exclusion"));
        userAuth = new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        UUID sessionId=UUID.randomUUID();
        when(adminSessions.autenticar(eq("synthetic-admin-session"), anyString()))
                .thenReturn(Optional.of(new AdminPrivilegedSessionService.AuthenticatedAdmin(staff,sessionId,
                        Instant.now().plusSeconds(3600),Instant.now().plusSeconds(1800))));
        when(clientIp.resolve(any(jakarta.servlet.http.HttpServletRequest.class))).thenReturn("127.0.0.1");
    }

    @AfterEach void preserveAppendOnlyConversationHistoryButRemoveSyntheticUsers() {
        if (jdbc != null && user != null) {
            jdbc.update("DELETE FROM public.admin_assignments WHERE usuario_id IN (?,?,?,?)", staff.getId(), eligibleRecipientId, auditorRecipientId, inactiveRecipientId);
            jdbc.update("DELETE FROM public.usuario_roles WHERE id_usuario IN (?,?,?,?,?)", user.getId(), staff.getId(), eligibleRecipientId, auditorRecipientId, inactiveRecipientId);
            jdbc.update("DELETE FROM public.usuarios WHERE id IN (?,?,?,?,?)", user.getId(), staff.getId(), eligibleRecipientId, auditorRecipientId, inactiveRecipientId);
        }
    }

    @Test void securedHttpFlowPersistsWaitingQueueClaimReplyPollingAndClosure() throws Exception {
        mvc.perform(post("/support-assistant/conversations").with(authentication(userAuth)).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("AI_ACTIVE"))
                .andDo(result -> conversationId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(result.getResponse().getContentAsByteArray()).get("id").asText()));

        String messageKey = UUID.randomUUID().toString();
        String message = "Preciso de orientação sobre o site.";
        var send = post("/support-assistant/conversations/" + conversationId + "/messages")
                .with(authentication(userAuth)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + message + "\",\"idempotencyKey\":\"" + messageKey + "\"}");
        mvc.perform(send).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("WAITING_HUMAN"));
        mvc.perform(post("/support-assistant/conversations/" + conversationId + "/messages")
                        .with(authentication(userAuth)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + message + "\",\"idempotencyKey\":\"" + messageKey + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/support-assistant/conversations").servletPath("/admin/support-assistant/conversations").with(authentication(userAuth)))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/support-assistant/conversations/" + conversationId).with(authentication(principalAuth(940000001))))
                .andExpect(status().isNotFound());
        assertConversationInWaitingQueueWithUnread(conversationId);
        mvc.perform(post("/admin/support-assistant/conversations/" + conversationId + "/take-over")
                        .servletPath("/admin/support-assistant/conversations/" + conversationId + "/take-over").cookie(adminCookie()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("HUMAN_ACTIVE"));
        mvc.perform(post("/admin/support-assistant/conversations/" + conversationId + "/messages")
                        .servletPath("/admin/support-assistant/conversations/" + conversationId + "/messages").cookie(adminCookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Vou orientar você por aqui.\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[?(@.body=='Vou orientar você por aqui.')]").isNotEmpty());
        mvc.perform(get("/support-assistant/conversations").with(authentication(userAuth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].unread").value(true));
        mvc.perform(get("/support-assistant/conversations/" + conversationId).with(authentication(userAuth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[?(@.body=='Vou orientar você por aqui.')]").isNotEmpty());
        mvc.perform(get("/support-assistant/conversations").with(authentication(userAuth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].unread").value(false));
        mvc.perform(get("/support-assistant/conversations").with(authentication(userAuth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(conversationId.toString()));
        mvc.perform(post("/admin/support-assistant/conversations/" + conversationId + "/close")
                        .servletPath("/admin/support-assistant/conversations/" + conversationId + "/close").cookie(adminCookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Atendimento concluído.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        assertEquals("CLOSED", jdbc.queryForObject("SELECT state FROM public.support_conversations WHERE id=?", String.class, conversationId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.support_messages WHERE conversation_id=? AND sender_type='USER' AND idempotency_key=?", Integer.class, conversationId, messageKey));
    }

    @Test void supportRecipientDirectoryIsPermissionFilteredAndTransferRechecksEligibility() throws Exception {
        mvc.perform(get("/admin/support-assistant/eligible-staff")
                        .servletPath("/admin/support-assistant/eligible-staff").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andDo(result -> assertStaffCandidates(result.getResponse().getContentAsByteArray(), eligibleRecipientId,
                        new int[] {staff.getId(), auditorRecipientId, inactiveRecipientId}));
        mvc.perform(get("/admin/support-assistant/eligible-staff")
                        .servletPath("/admin/support-assistant/eligible-staff").with(authentication(userAuth)))
                .andExpect(status().is4xxClientError());

        var created = mvc.perform(post("/support-assistant/conversations").with(authentication(userAuth)).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        UUID handoffConversation = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsByteArray()).get("id").asText());
        String messageKey = UUID.randomUUID().toString();
        mvc.perform(post("/support-assistant/conversations/" + handoffConversation + "/messages")
                        .with(authentication(userAuth)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Preciso de atendimento para transferir.\",\"idempotencyKey\":\"" + messageKey + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("WAITING_HUMAN"));
        mvc.perform(post("/admin/support-assistant/conversations/" + handoffConversation + "/take-over")
                        .servletPath("/admin/support-assistant/conversations/" + handoffConversation + "/take-over")
                        .cookie(adminCookie()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("HUMAN_ACTIVE"));
        String invalidTarget = "{\"targetStaffUserId\":" + auditorRecipientId + ",\"reason\":\"Tentativa sintética inválida.\"}";
        mvc.perform(post("/admin/support-assistant/conversations/" + handoffConversation + "/transfer")
                        .servletPath("/admin/support-assistant/conversations/" + handoffConversation + "/transfer")
                        .cookie(adminCookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidTarget))
                .andExpect(status().isBadRequest());
        String inactiveTarget = "{\"targetStaffUserId\":" + inactiveRecipientId + ",\"reason\":\"Tentativa sintética de destino inativo.\"}";
        mvc.perform(post("/admin/support-assistant/conversations/" + handoffConversation + "/transfer")
                        .servletPath("/admin/support-assistant/conversations/" + handoffConversation + "/transfer")
                        .cookie(adminCookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(inactiveTarget))
                .andExpect(status().isBadRequest());
        String validTarget = "{\"targetStaffUserId\":" + eligibleRecipientId + ",\"reason\":\"Encaminhamento sintético para teste.\"}";
        mvc.perform(post("/admin/support-assistant/conversations/" + handoffConversation + "/transfer")
                        .servletPath("/admin/support-assistant/conversations/" + handoffConversation + "/transfer")
                        .cookie(adminCookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validTarget))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("HUMAN_ACTIVE"));
        assertEquals(eligibleRecipientId, jdbc.queryForObject("SELECT assigned_staff_user_id FROM public.support_conversations WHERE id=?", Integer.class, handoffConversation));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.support_conversation_events WHERE conversation_id=? AND event_type='TRANSFERRED'", Integer.class, handoffConversation));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.support_messages WHERE conversation_id=? AND sender_type='USER' AND idempotency_key=?", Integer.class, handoffConversation, messageKey));
    }

    @Test void startingNewConversationClosesPendingRequestAndRetriesDoNotCreateDuplicates() throws Exception {
        var previousCreated = mvc.perform(post("/support-assistant/conversations").with(authentication(userAuth)).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        UUID previousConversationId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(previousCreated.getResponse().getContentAsByteArray()).get("id").asText());
        String previousMessageKey = UUID.randomUUID().toString();
        mvc.perform(post("/support-assistant/conversations/" + previousConversationId + "/messages")
                        .with(authentication(userAuth)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Solicito atendimento humano antes de trocar de conversa.\",\"idempotencyKey\":\"" + previousMessageKey + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("WAITING_HUMAN"));
        UUID newConversationId = UUID.randomUUID();
        String request = "{\"conversationId\":\"" + newConversationId + "\",\"previousConversationId\":\"" + previousConversationId + "\"}";
        var result = post("/support-assistant/conversations/new").with(authentication(userAuth)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request);
        mvc.perform(result).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(newConversationId.toString()))
                .andExpect(jsonPath("$.state").value("AI_ACTIVE"))
                .andExpect(jsonPath("$.messages[0].body").value("Olá! Sou a Sophie, assistente virtual do Porto Ajuda. Como posso ajudar?"));
        mvc.perform(get("/support-assistant/conversations/" + previousConversationId).with(authentication(userAuth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.contentExpiresAt").isNotEmpty())
                .andDo(response -> assertTrue(new String(response.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8)
                        .contains("histórico permanece disponível")));
        mvc.perform(post("/support-assistant/conversations/new").with(authentication(userAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(newConversationId.toString()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.support_conversations WHERE id=?", Integer.class, newConversationId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.support_conversations WHERE owner_user_id=? AND state<>'CLOSED'", Integer.class, user.getId()));
    }

    @Test void userResumeEndpointKeepsCsrfAndDoesNotCancelQueueWhenAiIsUnavailable() throws Exception {
        var created=mvc.perform(post("/support-assistant/conversations")
                        .with(authentication(userAuth)).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        UUID id=UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsByteArray()).get("id").asText());
        mvc.perform(post("/support-assistant/conversations/"+id+"/request-human")
                        .with(authentication(userAuth)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Solicito atendimento humano.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("WAITING_HUMAN"));

        mvc.perform(post("/support-assistant/conversations/"+id+"/continue-with-sophie")
                        .with(authentication(userAuth)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/support-assistant/conversations/"+id+"/continue-with-sophie")
                        .with(authentication(userAuth)).with(csrf()))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/support-assistant/conversations/"+id).with(authentication(userAuth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("WAITING_HUMAN"));
    }

    private static Usuario principal(int id) throws Exception {
        Usuario user = new Usuario(); Field field = Usuario.class.getDeclaredField("id"); field.setAccessible(true); field.set(user, id); return user;
    }

    private void ensureTestAdminDirectorySchema() {
        jdbc.execute("ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS nome VARCHAR(255)");
        jdbc.execute("ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS nome_social VARCHAR(255)");
        jdbc.execute("ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS ativo BOOLEAN NOT NULL DEFAULT TRUE");
        jdbc.execute("CREATE TABLE IF NOT EXISTS public.roles (id SERIAL PRIMARY KEY, nome VARCHAR(64) NOT NULL UNIQUE)");
        jdbc.update("INSERT INTO public.roles(nome) VALUES ('ADMIN') ON CONFLICT (nome) DO NOTHING");
        jdbc.execute("CREATE TABLE IF NOT EXISTS public.usuario_roles (id_usuario INTEGER NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE, id_role INTEGER NOT NULL REFERENCES public.roles(id) ON DELETE CASCADE, PRIMARY KEY(id_usuario,id_role))");
    }
    private static Cookie adminCookie() { return new Cookie(AdminSessionCookie.NAME,"synthetic-admin-session"); }
    private static UsernamePasswordAuthenticationToken principalAuth(int id) throws Exception {
        return new UsernamePasswordAuthenticationToken(principal(id), null, List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
    }

    private void assertConversationInWaitingQueueWithUnread(UUID id) throws Exception {
        for (int page=0; page<100; page++) {
            var response=mvc.perform(get("/admin/support-assistant/conversations?state=WAITING_HUMAN&page="+page+"&size=50")
                    .servletPath("/admin/support-assistant/conversations").cookie(adminCookie()))
                    .andExpect(status().isOk()).andReturn();
            var items=com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(response.getResponse().getContentAsByteArray()).path("items");
            for (var item : items) {
                if (id.toString().equals(item.path("id").asText())) {
                    assertTrue(item.path("unread").asBoolean(), "A new applicant message must be unread in the staff queue.");
                    return;
                }
            }
        }
        fail("Expected the synthetic conversation in the paginated staff waiting queue.");
    }

    private static void assertStaffCandidates(byte[] responseBody, int includedId, int[] excludedIds) throws Exception {
        var rows = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(responseBody);
        boolean included = false;
        for (var candidate : rows) {
            if (candidate.path("userId").asInt() == includedId) {
                included = true;
                assertFalse(candidate.path("displayName").asText().isBlank());
                assertFalse(candidate.has("email"));
                assertFalse(candidate.has("phone"));
            }
            for (int excluded : excludedIds) assertNotEquals(excluded, candidate.path("userId").asInt());
        }
        assertTrue(included, "An active moderator with SUPPORT_MANAGE is listed.");
    }
}
