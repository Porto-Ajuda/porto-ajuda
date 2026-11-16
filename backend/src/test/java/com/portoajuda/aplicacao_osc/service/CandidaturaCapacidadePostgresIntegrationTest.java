package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.admin.AdminAssignment;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentStatus;
import com.portoajuda.aplicacao_osc.admin.AdminAuthenticationDetails;
import com.portoajuda.aplicacao_osc.admin.AdminProfile;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.CandidaturasVoluntariado;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.Genero;
import com.portoajuda.aplicacao_osc.enums.ModalidadeVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.SessaoUsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import com.portoajuda.aplicacao_osc.service.AdminAuthCodeSecurity;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.service.AdminAuthDeliveryWorker;
import com.portoajuda.aplicacao_osc.service.EmailService;
import com.portoajuda.aplicacao_osc.service.SmsService;
import com.portoajuda.aplicacao_osc.service.SupabaseStorageService;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** End-to-end PostgreSQL tests for the actual volunteer application services and HTTP chain. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.security.railway-public-edge-enabled=true",
        "app.initialization.seed-roles=false",
        "app.maintenance.cleanup-cron=-",
        "app.admin-auth.controls-cleanup-cron=-",
        "app.admin.rbac.enforcement-enabled=true"
})
@EnabledIfEnvironmentVariable(named = "VOLUNTEER_APPLICATION_TEST_DATABASE_CONFIRMED", matches = "true")
class CandidaturaCapacidadePostgresIntegrationTest {
    private static final String PASSWORD = "SyntheticVolTest1!";

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired UsuarioRepository users;
    @Autowired RoleRepository roles;
    @Autowired OscRepository oscs;
    @Autowired OscMembrosRepository members;
    @Autowired PostRepository posts;
    @Autowired VagaRepository vacancies;
    @Autowired CandidaturasVoluntariadoRepository applications;
    @Autowired SessaoUsuarioRepository sessions;
    @Autowired AdminAuthChallengeRepository adminChallenges;
    @Autowired AdminAssignmentRepository adminAssignments;
    @Autowired AdminPrivilegedSessionService adminPrivilegedSessions;
    @Autowired OscStatusService oscStatusService;
    @Autowired PasswordEncoder encoder;
    @Autowired AdminAuthCodeSecurity abuseKey;
    @Autowired PlatformTransactionManager transactionManager;
    @LocalServerPort int port;

    @MockitoBean EmailService emailService;
    @MockitoBean SmsService smsService;
    @MockitoBean AdminAuthDeliveryWorker adminDeliveryWorker;
    @MockitoBean SupabaseStorageService storageService;

    private final java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
    private final Set<Integer> userIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> oscIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> postIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> vacancyIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> applicationIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> adminChallengeIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> adminSessionIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> adminAssignmentIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, String> accessCookies = new ConcurrentHashMap<>();
    private String ip;
    private String ledger;
    private boolean databaseConfirmed;
    private UserFixture creator;
    private UserFixture administrator;
    private UserFixture editor;
    private UserFixture member;
    private UserFixture unrelatedCreator;
    private UserFixture applicantOne;
    private UserFixture applicantTwo;
    private Osc osc;
    private Osc unrelatedOsc;
    private Post post;
    private Vaga vacancy;
    private Post unrelatedPost;
    private Vaga unrelatedVacancy;

    @BeforeEach
    void createOnlySyntheticFixturesAfterVerifyingTheAuthorizedDatabase() throws Exception {
        String jdbcUrl;
        String sslMode;
        try (var connection = dataSource.getConnection()) {
            jdbcUrl = connection.getMetaData().getURL();
        }
        sslMode = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class)
                .getDataSourceProperties().getProperty("sslmode");
        assertTrue(jdbcUrl.toLowerCase(java.util.Locale.ROOT).contains("supabase"),
                "This opt-in integration test is restricted to the authorized Supabase TEST database.");
        assertTrue(jdbcUrl.matches("(?i).*/postgres(?:[?].*)?$"),
                "The TEST target must be the expected postgres database.");
        assertEquals("require", sslMode, "The TEST database connection must require TLS.");
        databaseConfirmed = true;
        ledger = System.getenv("VOLUNTEER_APPLICATION_TEST_LEDGER");
        assertNotNull(ledger, "A task-local fixture ledger path is required.");
        assertFalse(ledger.isBlank(), "A task-local fixture ledger path is required.");
        ip = "198.51.100." + (1 + new SecureRandom().nextInt(254));

        Role userRole = roles.findByNome("USUARIO").orElseThrow();
        creator = createUser(userRole, "creator");
        administrator = createUser(userRole, "admin");
        editor = createUser(userRole, "editor");
        member = createUser(userRole, "member");
        unrelatedCreator = createUser(userRole, "other-owner");
        applicantOne = createUser(userRole, "applicant-a");
        applicantTwo = createUser(userRole, "applicant-b");

        osc = createOsc(creator.user(), "Candidaturas QA");
        createMembership(osc, creator.user(), CargoOsc.CRIADOR);
        createMembership(osc, administrator.user(), CargoOsc.ADMINISTRADOR);
        createMembership(osc, editor.user(), CargoOsc.EDITOR);
        createMembership(osc, member.user(), CargoOsc.MEMBRO);
        post = createOpportunity(osc, creator.user(), 2);
        vacancy = post.getVagaVoluntariado();

        unrelatedOsc = createOsc(unrelatedCreator.user(), "Outra ONG Candidaturas QA");
        createMembership(unrelatedOsc, unrelatedCreator.user(), CargoOsc.CRIADOR);
        unrelatedPost = createOpportunity(unrelatedOsc, unrelatedCreator.user(), 1);
        unrelatedVacancy = unrelatedPost.getVagaVoluntariado();
    }

    @AfterEach
    void removeAndVerifyOnlyThisRunSyntheticRecords() {
        if (!databaseConfirmed) return;
        for (Integer vacancyId : vacancyIds) {
            jdbc.update("delete from candidaturas_voluntariado where id_vaga = ?", vacancyId);
        }
        for (Integer applicationId : applicationIds) {
            jdbc.update("delete from candidaturas_voluntariado where id = ?", applicationId);
        }
        for (Integer vacancyId : vacancyIds) {
            jdbc.update("delete from vagas_voluntariado where id = ?", vacancyId);
        }
        for (Integer postId : postIds) {
            jdbc.update("delete from post where id = ?", postId);
        }
        for (Integer oscId : oscIds) {
            jdbc.update("delete from osc_membros where id_osc = ?", oscId);
            jdbc.update("delete from osc_causas where id_osc = ?", oscId);
            jdbc.update("delete from osc_ods where id_osc = ?", oscId);
            jdbc.update("delete from oscs where id = ?", oscId);
        }
        for (UUID sessionId : adminSessionIds) {
            jdbc.update("delete from admin_privileged_sessions where id = ?", sessionId);
        }
        for (UUID challengeId : adminChallengeIds) {
            jdbc.update("delete from admin_auth_challenges where id = ?", challengeId);
        }
        for (UUID assignmentId : adminAssignmentIds) {
            jdbc.update("delete from admin_assignments where id = ?", assignmentId);
        }
        for (Integer userId : userIds) {
            jdbc.update("delete from sessoes_usuario where usuario_id = ?", userId);
            jdbc.update("delete from usuario_roles where id_usuario = ?", userId);
            jdbc.update("delete from usuarios where id = ?", userId);
        }
        if (ip != null) {
            String key = abuseKey.hashIdentifier("control:START_IP", "user-login-ip:" + ip);
            jdbc.update("delete from admin_auth_controls where control_key = ?", key);
        }

        for (Integer id : applicationIds) {
            assertEquals(0, jdbc.queryForObject(
                    "select count(*) from candidaturas_voluntariado where id = ?", Integer.class, id));
        }
        for (Integer id : vacancyIds) {
            assertEquals(0, jdbc.queryForObject("select count(*) from vagas_voluntariado where id = ?", Integer.class, id));
            assertEquals(0, jdbc.queryForObject(
                    "select count(*) from candidaturas_voluntariado where id_vaga = ?", Integer.class, id));
        }
        for (Integer id : postIds) {
            assertEquals(0, jdbc.queryForObject("select count(*) from post where id = ?", Integer.class, id));
        }
        for (Integer id : oscIds) {
            assertEquals(0, jdbc.queryForObject("select count(*) from oscs where id = ?", Integer.class, id));
        }
        for (Integer id : userIds) {
            assertEquals(0, jdbc.queryForObject("select count(*) from usuarios where id = ?", Integer.class, id));
            assertEquals(0, jdbc.queryForObject("select count(*) from sessoes_usuario where usuario_id = ?", Integer.class, id));
        }
        for (UUID id : adminSessionIds) {
            assertEquals(0, jdbc.queryForObject("select count(*) from admin_privileged_sessions where id = ?", Integer.class, id));
        }
        for (UUID id : adminChallengeIds) {
            assertEquals(0, jdbc.queryForObject("select count(*) from admin_auth_challenges where id = ?", Integer.class, id));
        }
        for (UUID id : adminAssignmentIds) {
            assertEquals(0, jdbc.queryForObject("select count(*) from admin_assignments where id = ?", Integer.class, id));
        }
    }

    @Test
    void httpAuthorizationCsrfIdentityPrivacyAndCurrentMembershipAreEnforced() throws Exception {
        HttpResult unauthenticated = request("POST", applyPath(vacancy), Map.of("mensagem", "Teste"), null, Csrf.VALID);
        assertEquals(401, unauthenticated.status());

        String applicantCookie = login(applicantOne);
        HttpResult missingCsrf = request("POST", applyPath(vacancy), Map.of("mensagem", "Teste"),
                applicantCookie, Csrf.NONE);
        HttpResult invalidCsrf = request("POST", applyPath(vacancy), Map.of("mensagem", "Teste"),
                applicantCookie, Csrf.INVALID);
        assertEquals(403, missingCsrf.status());
        assertEquals(403, invalidCsrf.status());

        HttpResult spoofAttempt = request("POST", applyPath(vacancy),
                Map.of("mensagem", "Posso ajudar", "usuarioId", applicantTwo.user().getId()),
                applicantCookie, Csrf.VALID);
        assertTrue(spoofAttempt.status() == 201 || spoofAttempt.status() == 400);
        if (spoofAttempt.status() == 201) {
            Integer id = applicationId(spoofAttempt);
            assertEquals(applicantOne.user().getId(), applications.findById(id).orElseThrow().getUsuario().getId());
        }
        HttpResult application = spoofAttempt.status() == 201 ? spoofAttempt
                : request("POST", applyPath(vacancy), Map.of("mensagem", "Posso ajudar"), applicantCookie, Csrf.VALID);
        assertEquals(201, application.status());
        Integer applicationId = applicationId(application);

        JsonNode mine = json(request("GET", "/usuario/candidaturas", null, applicantCookie, Csrf.NONE));
        assertEquals(1, mine.path("content").size());
        assertFalse(mine.toString().toLowerCase(java.util.Locale.ROOT).contains("cpf"));
        assertFalse(mine.toString().toLowerCase(java.util.Locale.ROOT).contains("senha"));
        String otherApplicantCookie = login(applicantTwo);
        assertEquals(0, json(request("GET", "/usuario/candidaturas", null, otherApplicantCookie, Csrf.NONE))
                .path("content").size());
        assertEquals(404, request("PATCH", "/usuario/candidaturas/" + applicationId + "/cancelar",
                null, otherApplicantCookie, Csrf.VALID).status());

        String editorCookie = login(editor);
        String memberCookie = login(member);
        String unrelatedCookie = login(unrelatedCreator);
        String creatorCookie = login(creator);
        String adminCookie = login(administrator);
        assertEquals(403, request("GET", "/osc/" + osc.getId() + "/candidaturas", null, editorCookie, Csrf.NONE).status());
        assertEquals(403, request("GET", "/osc/" + osc.getId() + "/candidaturas", null, memberCookie, Csrf.NONE).status());
        assertEquals(403, request("GET", "/osc/" + osc.getId() + "/candidaturas", null, unrelatedCookie, Csrf.NONE).status());
        HttpResult managerList = request("GET", "/osc/" + osc.getId() + "/candidaturas", null, adminCookie, Csrf.NONE);
        assertEquals(200, managerList.status());
        assertTrue(managerList.body().contains("emailCandidato"));
        assertFalse(managerList.body().toLowerCase(java.util.Locale.ROOT).contains("cpf"));
        assertFalse(managerList.body().toLowerCase(java.util.Locale.ROOT).contains("senha"));
        assertEquals(403, request("PATCH", "/osc/" + osc.getId() + "/candidaturas/" + applicationId,
                Map.of("status", "APROVADA"), editorCookie, Csrf.VALID).status());
        assertEquals(404, request("PATCH", "/osc/" + unrelatedOsc.getId() + "/candidaturas/" + applicationId,
                Map.of("status", "APROVADA"), unrelatedCookie, Csrf.VALID).status());
        assertEquals(200, request("GET", "/osc/" + osc.getId() + "/candidaturas", null, creatorCookie, Csrf.NONE).status());

        OscMembros membership = members.findByOsc_IdAndUsuario_Id(osc.getId(), administrator.user().getId()).orElseThrow();
        membership.setCargoOsc(CargoOsc.EDITOR);
        members.saveAndFlush(membership);
        assertEquals(403, request("GET", "/osc/" + osc.getId() + "/candidaturas", null, adminCookie, Csrf.NONE).status());
        assertEquals(403, request("PATCH", "/osc/" + osc.getId() + "/candidaturas/" + applicationId,
                Map.of("status", "APROVADA"), adminCookie, Csrf.VALID).status());
        assertEquals(StatusCandidatura.PENDENTE, applications.findById(applicationId).orElseThrow().getStatus());
    }

    @Test
    void competingCreatorTransfersLeaveExactlyOneCurrentCreator() throws Exception {
        PreparedRequest toAdministrator = prepare("PATCH",
                "/osc/" + osc.getId() + "/membros/" + administrator.user().getId() + "/cargo",
                Map.of("cargo", "CRIADOR"), login(creator), Csrf.VALID);
        PreparedRequest toEditor = prepare("PATCH",
                "/osc/" + osc.getId() + "/membros/" + editor.user().getId() + "/cargo",
                Map.of("cargo", "CRIADOR"), login(creator), Csrf.VALID);

        List<HttpResult> responses = simultaneously(toAdministrator, toEditor);

        assertEquals(1, responses.stream().filter(response -> response.status() == 204).count());
        assertEquals(1, responses.stream().filter(response -> response.status() == 403).count());
        Integer persistedCreatorId = jdbc.queryForObject(
                "select id_usuario_criador from oscs where id = ?", Integer.class, osc.getId());
        assertNotNull(persistedCreatorId);
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from osc_membros where id_osc = ? and cargo_osc = 'CRIADOR'",
                Integer.class, osc.getId()));
        assertEquals(persistedCreatorId, jdbc.queryForObject(
                "select id_usuario from osc_membros where id_osc = ? and cargo_osc = 'CRIADOR'",
                Integer.class, osc.getId()));
        assertEquals(CargoOsc.ADMINISTRADOR, members.findByOsc_IdAndUsuario_Id(
                osc.getId(), creator.user().getId()).orElseThrow().getCargoOsc());
    }

    @Test
    void creatorTransferRacingTargetRemovalHasOneConsistentOutcome() throws Exception {
        String targetEmail = editor.email();
        String encodedEmail = java.net.URLEncoder.encode(targetEmail, StandardCharsets.UTF_8);
        PreparedRequest transfer = prepare("PATCH",
                "/osc/" + osc.getId() + "/membros/" + editor.user().getId() + "/cargo",
                Map.of("cargo", "CRIADOR"), login(creator), Csrf.VALID);
        PreparedRequest removal = prepare("DELETE",
                "/osc/" + osc.getId() + "/membros?email=" + encodedEmail,
                null, login(creator), Csrf.VALID);

        List<HttpResult> responses = simultaneously(transfer, removal);
        int transferStatus = responses.get(0).status();
        int removalStatus = responses.get(1).status();
        Integer persistedCreatorId = jdbc.queryForObject(
                "select id_usuario_criador from oscs where id = ?", Integer.class, osc.getId());

        if (transferStatus == 204) {
            assertEquals(403, removalStatus);
            assertEquals(editor.user().getId(), persistedCreatorId);
            assertEquals(CargoOsc.CRIADOR, members.findByOsc_IdAndUsuario_Id(
                    osc.getId(), editor.user().getId()).orElseThrow().getCargoOsc());
            assertEquals(CargoOsc.ADMINISTRADOR, members.findByOsc_IdAndUsuario_Id(
                    osc.getId(), creator.user().getId()).orElseThrow().getCargoOsc());
        } else {
            assertEquals(404, transferStatus);
            assertEquals(204, removalStatus);
            assertEquals(creator.user().getId(), persistedCreatorId);
            assertTrue(members.findByOsc_IdAndUsuario_Id(osc.getId(), editor.user().getId()).isEmpty());
        }
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from osc_membros where id_osc = ? and cargo_osc = 'CRIADOR'",
                Integer.class, osc.getId()));
    }

    @Test
    void memberRevocationSerializesWithContentMutationAndUsesCurrentMembership() throws Exception {
        Post memberOwnedPost = new Post();
        memberOwnedPost.setOsc(osc);
        memberOwnedPost.setUsuario(editor.user());
        memberOwnedPost.setTipo(TipoPost.DOACAO);
        memberOwnedPost.setAtivo(true);
        memberOwnedPost.setTitulo("Publicação sintética do editor");
        memberOwnedPost.setConteudo("Conteúdo sintético para teste de autorização.");
        memberOwnedPost = posts.saveAndFlush(memberOwnedPost);
        postIds.add(memberOwnedPost.getId());
        recordFixture("POST", memberOwnedPost.getId());
        final Integer targetPostId = memberOwnedPost.getId();

        String editorCookie = login(editor);
        CountDownLatch revocationLockHeld = new CountDownLatch(1);
        CountDownLatch commitRevocation = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> revocation = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select id from oscs where id = ? for update", Integer.class, osc.getId());
                jdbc.update("delete from osc_membros where id_osc = ? and id_usuario = ?",
                        osc.getId(), editor.user().getId());
                revocationLockHeld.countDown();
                try {
                    assertTrue(commitRevocation.await(15, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while synchronizing synthetic membership revocation.", interrupted);
                }
                return null;
            }));
            assertTrue(revocationLockHeld.await(10, TimeUnit.SECONDS));

            Map<String, Object> body = Map.of(
                    "id", targetPostId,
                    "post", Map.of("tipo", "DOACAO", "ativo", true,
                            "titulo", "Tentativa após revogação", "conteudo", "Conteúdo alterado"));
            Future<HttpResult> update = pool.submit(() -> request("PUT",
                    "/post/osc/" + osc.getId() + "/put", body, editorCookie, Csrf.VALID));
            assertTrue(awaitPostgresOrganizationLockWait(),
                    "The content mutation should wait for the organization serialization lock.");
            commitRevocation.countDown();
            revocation.get(15, TimeUnit.SECONDS);
            HttpResult response = update.get(20, TimeUnit.SECONDS);

            assertEquals(403, response.status());
            assertTrue(members.findByOsc_IdAndUsuario_Id(osc.getId(), editor.user().getId()).isEmpty());
            assertEquals("Publicação sintética do editor", posts.findById(targetPostId).orElseThrow().getTitulo());
        } finally {
            commitRevocation.countDown();
        }
    }

    @Test
    void archivedOrganizationDoesNotExposeCandidateManagementData() throws Exception {
        String applicantCookie = login(applicantOne);
        HttpResult application = request("POST", applyPath(vacancy), Map.of("mensagem", "Inscrição sintética"),
                applicantCookie, Csrf.VALID);
        assertEquals(201, application.status());
        Integer applicationId = applicationId(application);
        jdbc.update("update oscs set data_arquivamento = ? where id = ?",
                LocalDateTime.now(), osc.getId());

        String administratorCookie = login(administrator);
        assertEquals(404, request("GET", "/osc/" + osc.getId() + "/candidaturas", null,
                administratorCookie, Csrf.NONE).status());
        assertEquals(404, request("PATCH", "/osc/" + osc.getId() + "/candidaturas/" + applicationId,
                Map.of("status", "RECUSADA"), administratorCookie, Csrf.VALID).status());
        assertEquals(StatusCandidatura.PENDENTE, applications.findById(applicationId).orElseThrow().getStatus());
    }

    @Test
    void httpProfileUpdateRejectsCraftedOrganizationIdentityChangesWithoutPartialWrites() throws Exception {
        String originalName = jdbc.queryForObject("select nome from oscs where id = ?", String.class, osc.getId());
        String originalDescription = jdbc.queryForObject(
                "select descricao from oscs where id = ?", String.class, osc.getId());
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("cnpj", osc.getCnpj().getCnpj());
        body.put("nome", "Nome alterado indevidamente");
        body.put("descricaoCurta", "Descrição curta sintética");
        body.put("descricao", "Tentativa de alteração parcial");
        body.put("email", "update-" + UUID.randomUUID() + "@example.invalid");
        body.put("areaGeografica", "cidade");
        body.put("pais", "Brasil");
        body.put("estado", "SP");
        body.put("cidade", "Santos");
        body.put("cep", "11000000");
        body.put("bairro", "Centro");
        body.put("rua", "Rua de teste");

        HttpResult response = request("PUT", "/osc/put", body, login(editor), Csrf.VALID);

        assertEquals(422, response.status());
        assertEquals(originalName, jdbc.queryForObject(
                "select nome from oscs where id = ?", String.class, osc.getId()));
        assertEquals(originalDescription, jdbc.queryForObject(
                "select descricao from oscs where id = ?", String.class, osc.getId()));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from osc_membros where id_osc = ? and id_usuario = ? and cargo_osc = 'EDITOR'",
                Integer.class, osc.getId(), editor.user().getId()));
    }

    @Test
    void httpProfileUpdatePersistsBothVolunteerAcceptanceChoicesAcrossReload() throws Exception {
        osc.setPais("Brasil");
        osc.setEstado("SP");
        osc.setAreaGeografica("cidade");
        jdbc.update("update oscs set pais = ?, estado = ?, area_geografica = ? where id = ?",
                "Brasil", "SP", "cidade", osc.getId());
        String creatorCookie = login(creator);

        for (boolean acceptsVolunteers : List.of(true, false)) {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("cnpj", osc.getCnpj().getCnpj());
            body.put("descricaoCurta", "Perfil sintético de voluntariado");
            body.put("descricao", "Descrição de teste da organização sintética.");
            body.put("email", osc.getEmail().valor());
            body.put("areaGeografica", "cidade");
            body.put("pais", "Brasil");
            body.put("estado", "SP");
            body.put("cidade", "Santos");
            body.put("cep", "11000000");
            body.put("bairro", "Centro");
            body.put("rua", "Rua de teste");
            body.put("aceitaVoluntarios", acceptsVolunteers);
            body.put("frequenciaVoluntariado", "Semanal");
            body.put("idadeMinimaVoluntario", 18);
            body.put("processoSeletivoVoluntario", true);
            body.put("emiteCertificadoVoluntario", false);
            body.put("atividadesVoluntariado", "Apoio comunitário sintético");

            HttpResult saved = request("PUT", "/osc/put", body, creatorCookie, Csrf.VALID);
            assertEquals(204, saved.status());

            HttpResult reload = request("GET", "/osc/" + osc.getId() + "/edicao", null,
                    creatorCookie, Csrf.NONE);
            assertEquals(200, reload.status());
            JsonNode profile = json(reload);
            assertEquals(acceptsVolunteers, profile.path("aceitaVoluntarios").booleanValue());
            assertEquals("Semanal", profile.path("frequenciaVoluntariado").asText());
            assertEquals(18, profile.path("idadeMinimaVoluntario").asInt());
            assertEquals("Apoio comunitário sintético", profile.path("atividadesVoluntariado").asText());
        }
    }

    @Test
    void memberActivationSerializesWithPlatformDeactivationInIndependentTransactions() throws Exception {
        AdminFixture platform = createPlatformAdminSession();
        String creatorCookie = login(creator);
        PreparedRequest memberReactivation = prepare("PATCH", "/osc/" + osc.getId() + "/status",
                Map.of("status", "ATIVA"), creatorCookie, Csrf.VALID);
        long existingAuditCount = countOrganizationStatusAudits(platform.user().getId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<HttpResult> memberResult = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(15, TimeUnit.SECONDS));
                return send(memberReactivation);
            });
            Future<Integer> platformResult = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(15, TimeUnit.SECONDS));
                return new TransactionTemplate(transactionManager).execute(transaction -> {
                    var authenticated = adminPrivilegedSessions.autenticar(
                            platform.rawCredential(), ip).orElseThrow();
                    List<GrantedAuthority> authorities = new ArrayList<>(authenticated.usuario().getAuthorities());
                    authorities.add(new SimpleGrantedAuthority("ADMIN_SESSION"));
                    var authentication = new UsernamePasswordAuthenticationToken(
                            authenticated.usuario(), null, authorities);
                    authentication.setDetails(new AdminAuthenticationDetails(authenticated.sessionId()));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    try {
                        oscStatusService.atualizarPelaPlataforma(osc.getId(),
                                new com.portoajuda.aplicacao_osc.dto.request.RequestAtualizarStatusOscDTO(
                                        StatusOsc.INATIVA, "Bloqueio sintético de concorrência"),
                                authenticated.usuario());
                        return 204;
                    } finally {
                        transaction.setRollbackOnly();
                        SecurityContextHolder.clearContext();
                    }
                });
            });
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            HttpResult memberResponse = memberResult.get(45, TimeUnit.SECONDS);
            Integer platformStatus = platformResult.get(45, TimeUnit.SECONDS);

            assertTrue(memberResponse.status() == 403 || memberResponse.status() == 409);
            assertEquals(204, platformStatus);
            assertEquals(StatusOsc.ATIVA, oscs.findById(osc.getId()).orElseThrow().getStatus());
            assertEquals(existingAuditCount, countOrganizationStatusAudits(platform.user().getId()));
        }
    }


    @Test
    void statusTransitionsAndReapplicationPolicyRemainConsistent() throws Exception {
        String applicantCookie = login(applicantOne);
        HttpResult first = request("POST", applyPath(vacancy), Map.of("mensagem", "Primeira inscrição"), applicantCookie, Csrf.VALID);
        assertEquals(201, first.status());
        Integer firstId = applicationId(first);
        assertEquals(200, request("PATCH", "/usuario/candidaturas/" + firstId + "/cancelar", null,
                applicantCookie, Csrf.VALID).status());
        assertEquals(StatusCandidatura.DESISTIU, applications.findById(firstId).orElseThrow().getStatus());

        HttpResult reapplied = request("POST", applyPath(vacancy), Map.of("mensagem", "Segunda inscrição"),
                applicantCookie, Csrf.VALID);
        assertEquals(201, reapplied.status());
        Integer secondId = applicationId(reapplied);
        String adminCookie = login(administrator);
        HttpResult rejected = request("PATCH", "/osc/" + osc.getId() + "/candidaturas/" + secondId,
                Map.of("status", "RECUSADA"), adminCookie, Csrf.VALID);
        assertEquals(200, rejected.status());
        assertEquals(StatusCandidatura.RECUSADA, applications.findById(secondId).orElseThrow().getStatus());
        assertEquals(409, request("POST", applyPath(vacancy), Map.of("mensagem", "Tentativa repetida"),
                applicantCookie, Csrf.VALID).status());
        assertEquals(409, request("PATCH", "/osc/" + osc.getId() + "/candidaturas/" + secondId,
                Map.of("status", "APROVADA"), adminCookie, Csrf.VALID).status());
        assertEquals(409, request("PATCH", "/usuario/candidaturas/" + secondId + "/cancelar",
                null, applicantCookie, Csrf.VALID).status());
    }

    @Test
    void publicVisibilityAndApplicationAvailabilityRespectCurrentNgoAndOpportunityState() throws Exception {
        String applicantCookie = login(applicantOne);
        HttpResult created = request("POST", applyPath(vacancy), Map.of("mensagem", "Histórico"), applicantCookie, Csrf.VALID);
        assertEquals(201, created.status());
        Integer appId = applicationId(created);
        assertEquals(200, request("GET", "/post/osc/" + osc.getId() + "/publicos/" + post.getId(), null, null, Csrf.NONE).status());
        HttpResult availability = request("GET", availabilityPath(vacancy), null, null, Csrf.NONE);
        assertTrue(json(availability).path("aceitaCandidaturas").asBoolean());
        assertFalse(availability.body().contains(applicantOne.email()));

        jdbc.update("update oscs set status = ? where id = ?", StatusOsc.INATIVA.name(), osc.getId());
        assertEquals(404, request("GET", "/post/osc/" + osc.getId() + "/publicos/" + post.getId(), null, null, Csrf.NONE).status());
        assertFalse(json(request("GET", availabilityPath(vacancy), null, null, Csrf.NONE))
                .path("aceitaCandidaturas").asBoolean());
        assertEquals(409, request("POST", applyPath(vacancy), Map.of("mensagem", "Depois da inativação"),
                login(applicantTwo), Csrf.VALID).status());
        assertEquals(StatusCandidatura.PENDENTE, applications.findById(appId).orElseThrow().getStatus());
        JsonNode historical = json(request("GET", "/usuario/candidaturas", null, applicantCookie, Csrf.NONE));
        assertEquals(1, historical.path("content").size());
        assertFalse(historical.path("content").get(0).path("oportunidadePublica").asBoolean());

        jdbc.update("update oscs set status = ? where id = ?", StatusOsc.ATIVA.name(), osc.getId());
        jdbc.update("update post set ativo = false where id = ?", post.getId());
        assertEquals(404, request("GET", "/post/osc/" + osc.getId() + "/publicos/" + post.getId(), null, null, Csrf.NONE).status());
        assertEquals(409, request("POST", applyPath(vacancy), Map.of("mensagem", "Post inativo"),
                login(applicantTwo), Csrf.VALID).status());
        jdbc.update("update post set ativo = true where id = ?", post.getId());
        jdbc.update("update vagas_voluntariado set status = ? where id = ?",
                StatusVaga.ENCERRADA.name(), vacancy.getId());
        assertEquals(404, request("GET", "/post/osc/" + osc.getId() + "/publicos/" + post.getId(), null, null, Csrf.NONE).status());
        assertEquals(409, request("POST", applyPath(vacancy), Map.of("mensagem", "Vaga encerrada"),
                login(applicantTwo), Csrf.VALID).status());
        jdbc.update("update vagas_voluntariado set status = ?, data_fim = ? where id = ?",
                StatusVaga.ABERTA.name(), LocalDate.now().minusDays(1), vacancy.getId());
        assertEquals(404, request("GET", "/post/osc/" + osc.getId() + "/publicos/" + post.getId(), null, null, Csrf.NONE).status());
        assertEquals(409, request("POST", applyPath(vacancy), Map.of("mensagem", "Vaga expirada"),
                login(applicantTwo), Csrf.VALID).status());
        jdbc.update("update oscs set status = ?, data_arquivamento = now() where id = ?",
                StatusOsc.ATIVA.name(), osc.getId());
        jdbc.update("update vagas_voluntariado set status = ?, data_fim = ? where id = ?",
                StatusVaga.ABERTA.name(), LocalDate.now().plusDays(5), vacancy.getId());
        assertEquals(404, request("GET", "/post/osc/" + osc.getId() + "/publicos/" + post.getId(), null, null, Csrf.NONE).status());
        assertFalse(json(request("GET", availabilityPath(vacancy), null, null, Csrf.NONE))
                .path("aceitaCandidaturas").asBoolean());
        assertEquals(409, request("POST", applyPath(vacancy), Map.of("mensagem", "ONG arquivada"),
                login(applicantTwo), Csrf.VALID).status());
    }

    @Test
    void concurrentApprovalsCompeteForTheLastPlaceThroughTheHttpApi() throws Exception {
        vacancy.setQuantidadeVagas(1);
        vacancies.saveAndFlush(vacancy);
        Integer firstId = createPending(vacancy, applicantOne.user());
        Integer secondId = createPending(vacancy, applicantTwo.user());
        String managerCookie = login(administrator);
        PreparedRequest first = prepare("PATCH", decisionPath(osc, firstId), Map.of("status", "APROVADA"), managerCookie, Csrf.VALID);
        PreparedRequest second = prepare("PATCH", decisionPath(osc, secondId), Map.of("status", "APROVADA"), managerCookie, Csrf.VALID);

        List<HttpResult> responses = simultaneously(first, second);
        assertEquals(1, responses.stream().filter(response -> response.status() == 200).count());
        assertEquals(1, responses.stream().filter(response -> response.status() == 409).count());
        assertEquals(1, approvedCount(vacancy));
        assertEquals(1, List.of(firstId, secondId).stream()
                .filter(id -> applications.findById(id).orElseThrow().getStatus() == StatusCandidatura.APROVADA).count());
        assertTrue(approvedCount(vacancy) <= vacancy.getQuantidadeVagas());
        CandidaturasVoluntariado approved = applications.findById(firstId).orElseThrow().getStatus()
                == StatusCandidatura.APROVADA ? applications.findById(firstId).orElseThrow()
                        : applications.findById(secondId).orElseThrow();
        String approvedApplicantCookie = approved.getUsuario().getId().equals(applicantOne.user().getId())
                ? login(applicantOne) : login(applicantTwo);
        assertEquals(409, request("PATCH", "/usuario/candidaturas/" + approved.getId() + "/cancelar",
                null, approvedApplicantCookie, Csrf.VALID).status());
    }

    @Test
    void concurrentDuplicateSubmissionsByTheSameApplicantCreateExactlyOneRecord() throws Exception {
        String applicantCookie = login(applicantOne);
        PreparedRequest first = prepare("POST", applyPath(vacancy), Map.of("mensagem", "Concorrente"), applicantCookie, Csrf.VALID);
        PreparedRequest second = prepare("POST", applyPath(vacancy), Map.of("mensagem", "Concorrente"), applicantCookie, Csrf.VALID);

        List<HttpResult> responses = simultaneously(first, second);
        assertEquals(1, responses.stream().filter(response -> response.status() == 201).count());
        assertEquals(1, responses.stream().filter(response -> response.status() == 409).count());
        responses.stream().filter(response -> response.status() == 201).findFirst().ifPresent(response -> {
            try { applicationId(response); } catch (IOException failure) { throw new IllegalStateException(failure); }
        });
        assertEquals(1, jdbc.queryForObject("select count(*) from candidaturas_voluntariado where id_vaga = ? and id_usuario = ?",
                Integer.class, vacancy.getId(), applicantOne.user().getId()));
    }

    @Test
    void approvalAndApplicantCancellationOfTheSamePendingApplicationHaveOneWinner() throws Exception {
        Integer applicationId = createPending(vacancy, applicantOne.user());
        PreparedRequest approval = prepare("PATCH", decisionPath(osc, applicationId),
                Map.of("status", "APROVADA"), login(administrator), Csrf.VALID);
        PreparedRequest cancellation = prepare("PATCH", "/usuario/candidaturas/" + applicationId + "/cancelar",
                null, login(applicantOne), Csrf.VALID);

        List<HttpResult> responses = simultaneously(approval, cancellation);
        assertEquals(1, responses.stream().filter(response -> response.status() == 200).count());
        assertEquals(1, responses.stream().filter(response -> response.status() == 409).count());
        StatusCandidatura finalStatus = applications.findById(applicationId).orElseThrow().getStatus();
        assertTrue(finalStatus == StatusCandidatura.APROVADA || finalStatus == StatusCandidatura.DESISTIU);
        assertEquals(finalStatus == StatusCandidatura.APROVADA ? 1 : 0, approvedCount(vacancy));
    }

    @Test
    void pendingCancellationAndApprovalOfAnotherCandidateRemainConsistentAtCapacity() throws Exception {
        vacancy.setQuantidadeVagas(1);
        vacancies.saveAndFlush(vacancy);
        Integer cancellingId = createPending(vacancy, applicantOne.user());
        Integer approvingId = createPending(vacancy, applicantTwo.user());
        PreparedRequest cancellation = prepare("PATCH", "/usuario/candidaturas/" + cancellingId + "/cancelar",
                null, login(applicantOne), Csrf.VALID);
        PreparedRequest approval = prepare("PATCH", decisionPath(osc, approvingId),
                Map.of("status", "APROVADA"), login(administrator), Csrf.VALID);

        List<HttpResult> responses = simultaneously(cancellation, approval);
        assertTrue(responses.stream().allMatch(response -> response.status() == 200));
        assertEquals(StatusCandidatura.DESISTIU, applications.findById(cancellingId).orElseThrow().getStatus());
        assertEquals(StatusCandidatura.APROVADA, applications.findById(approvingId).orElseThrow().getStatus());
        assertEquals(1, approvedCount(vacancy));
        assertTrue(approvedCount(vacancy) <= vacancy.getQuantidadeVagas());
    }

    @Test
    void capacityReductionRacingApprovalNeverLeavesApprovedCountAboveCapacity() throws Exception {
        Integer alreadyApproved = createPending(vacancy, applicantOne.user());
        Integer waiting = createPending(vacancy, applicantTwo.user());
        assertEquals(200, request("PATCH", decisionPath(osc, alreadyApproved), Map.of("status", "APROVADA"),
                login(administrator), Csrf.VALID).status());
        String ownerCookie = login(creator);
        PreparedRequest approval = prepare("PATCH", decisionPath(osc, waiting),
                Map.of("status", "APROVADA"), login(administrator), Csrf.VALID);
        PreparedRequest capacityChange = prepare("PUT", "/post/osc/" + osc.getId() + "/put",
                updatePostBody(post, 1), ownerCookie, Csrf.VALID);

        List<HttpResult> responses = simultaneously(approval, capacityChange);
        int approvalStatus = responses.get(0).status();
        int capacityStatus = responses.get(1).status();
        assertTrue((approvalStatus == 200 && capacityStatus == 409)
                || (approvalStatus == 409 && capacityStatus == 200));
        Integer storedCapacity = vacancies.findById(vacancy.getId()).orElseThrow().getQuantidadeVagas();
        long storedApprovals = approvedCount(vacancy);
        assertTrue(storedApprovals <= storedCapacity,
                "The persisted capacity edit and decision must share the vacancy lock.");
        assertEquals(approvalStatus == 200 ? StatusCandidatura.APROVADA : StatusCandidatura.PENDENTE,
                applications.findById(waiting).orElseThrow().getStatus());
    }

    @Test
    void capacityCannotBeReducedBelowExistingApprovals() throws Exception {
        Integer first = createPending(vacancy, applicantOne.user());
        Integer second = createPending(vacancy, applicantTwo.user());
        String managerCookie = login(administrator);
        assertEquals(200, request("PATCH", decisionPath(osc, first), Map.of("status", "APROVADA"),
                managerCookie, Csrf.VALID).status());
        assertEquals(200, request("PATCH", decisionPath(osc, second), Map.of("status", "APROVADA"),
                managerCookie, Csrf.VALID).status());

        HttpResult reduced = request("PUT", "/post/osc/" + osc.getId() + "/put", updatePostBody(post, 1),
                login(creator), Csrf.VALID);

        assertEquals(409, reduced.status());
        assertEquals(2, vacancies.findById(vacancy.getId()).orElseThrow().getQuantidadeVagas());
        assertEquals(2, approvedCount(vacancy));
    }

    @Test
    void applicationCreationRacingOpportunityClosureNeverAcceptsSubsequentRequests() throws Exception {
        PreparedRequest apply = prepare("POST", applyPath(vacancy), Map.of("mensagem", "Corrida vaga"),
                login(applicantOne), Csrf.VALID);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<HttpResult> applicationAttempt = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(15, TimeUnit.SECONDS));
                return send(apply);
            });
            Future<?> closeOpportunity = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(15, TimeUnit.SECONDS));
                new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                    Vaga locked = vacancies.findForUpdateById(vacancy.getId()).orElseThrow();
                    locked.setStatus(StatusVaga.ENCERRADA);
                    vacancies.saveAndFlush(locked);
                });
                return null;
            });
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            HttpResult attempted = applicationAttempt.get(45, TimeUnit.SECONDS);
            closeOpportunity.get(45, TimeUnit.SECONDS);
            assertTrue(attempted.status() == 201 || attempted.status() == 409);
            assertEquals(StatusVaga.ENCERRADA, vacancies.findById(vacancy.getId()).orElseThrow().getStatus());
            if (attempted.status() == 201) applicationId(attempted);
        }
        assertEquals(409, request("POST", applyPath(vacancy), Map.of("mensagem", "Após encerramento"),
                login(applicantTwo), Csrf.VALID).status());
        assertTrue(jdbc.queryForObject("select count(*) from candidaturas_voluntariado where id_vaga = ?",
                Integer.class, vacancy.getId()) <= 1);
    }

    @Test
    void applicationCreationRacingNgoDeactivationNeverAcceptsSubsequentRequests() throws Exception {
        PreparedRequest apply = prepare("POST", applyPath(vacancy), Map.of("mensagem", "Corrida ONG"),
                login(applicantOne), Csrf.VALID);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<HttpResult> applicationAttempt = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(15, TimeUnit.SECONDS));
                return send(apply);
            });
            Future<?> deactivateOrganization = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(15, TimeUnit.SECONDS));
                new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                    Osc locked = oscs.findForUpdateById(osc.getId()).orElseThrow();
                    locked.setStatus(StatusOsc.INATIVA);
                    oscs.saveAndFlush(locked);
                });
                return null;
            });
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            HttpResult attempted = applicationAttempt.get(45, TimeUnit.SECONDS);
            deactivateOrganization.get(45, TimeUnit.SECONDS);
            assertTrue(attempted.status() == 201 || attempted.status() == 409);
            assertEquals(StatusOsc.INATIVA, oscs.findById(osc.getId()).orElseThrow().getStatus());
            if (attempted.status() == 201) applicationId(attempted);
        }
        assertEquals(409, request("POST", applyPath(vacancy), Map.of("mensagem", "Após inativação"),
                login(applicantTwo), Csrf.VALID).status());
        assertTrue(jdbc.queryForObject("select count(*) from candidaturas_voluntariado where id_vaga = ?",
                Integer.class, vacancy.getId()) <= 1);
    }

    private UserFixture createUser(Role role, String label) throws IOException {
        String email = "volunteer-it-" + label + "-" + UUID.randomUUID() + "@example.invalid";
        Usuario user = new Usuario();
        user.setCpf(new Cpf(validCpf()));
        user.setEmail(new Email(email));
        user.setNome("Conta sintetica voluntariado " + label);
        user.setDataNascimento(LocalDate.of(1990, 1, 1));
        user.setGenero(Genero.PREFIRO_NAO_INFORMAR);
        user.setSenha(encoder.encode(PASSWORD));
        user.setEmailVerificado(true);
        user.setTelefoneVerificado(false);
        user.setAtivo(true);
        user.setRoles(new java.util.HashSet<>(Set.of(role)));
        user = users.saveAndFlush(user);
        assertNotEquals(47, user.getId());
        assertNotEquals(48, user.getId());
        userIds.add(user.getId());
        recordFixture("USER", user.getId());
        return new UserFixture(user, email);
    }

    private Osc createOsc(Usuario owner, String name) throws IOException {
        Osc organization = new Osc();
        organization.setCnpj(new Cnpj(validCnpj()));
        organization.setUsuario(owner);
        organization.setNome(name + " " + UUID.randomUUID().toString().substring(0, 8));
        organization.setDescricao("Fixture sintetica de teste automatizado.");
        organization.setEmail(new Email("ngo-" + UUID.randomUUID() + "@example.invalid"));
        organization.setCidade("Santos");
        organization.setCep("11000000");
        organization.setBairro("Centro");
        organization.setRua("Rua de teste");
        organization.setStatus(StatusOsc.ATIVA);
        organization = oscs.saveAndFlush(organization);
        oscIds.add(organization.getId());
        recordFixture("OSC", organization.getId());
        return organization;
    }

    private void createMembership(Osc organization, Usuario user, CargoOsc role) {
        OscMembros membership = new OscMembros();
        membership.setOsc(organization);
        membership.setUsuario(user);
        membership.setCargoOsc(role);
        membership.setDataCriacao(LocalDateTime.now());
        membership.inicializarId();
        members.saveAndFlush(membership);
    }

    private Post createOpportunity(Osc organization, Usuario author, int capacity) throws IOException {
        Post opportunity = new Post();
        opportunity.setOsc(organization);
        opportunity.setUsuario(author);
        opportunity.setTipo(TipoPost.VOLUNTARIADO);
        opportunity.setAtivo(true);
        opportunity.setTitulo("Vaga sintetica de voluntariado");
        opportunity.setConteudo("Descricao de teste sem dados reais.");
        opportunity = posts.saveAndFlush(opportunity);
        postIds.add(opportunity.getId());
        recordFixture("POST", opportunity.getId());

        Vaga vacancy = new Vaga();
        vacancy.setPost(opportunity);
        vacancy.setQuantidadeVagas(capacity);
        vacancy.setModalidade(ModalidadeVoluntariado.PRESENCIAL);
        vacancy.setRequisitos("Fixture de teste.");
        vacancy.setCargaHoraria("4 horas semanais");
        vacancy.setCidade("Santos");
        vacancy.setBairro("Centro");
        vacancy.setRua("Rua de teste");
        vacancy.setNumero("1");
        vacancy.setDataInicio(LocalDate.now());
        vacancy.setDataFim(LocalDate.now().plusDays(30));
        vacancy.setStatus(StatusVaga.ABERTA);
        vacancy = vacancies.saveAndFlush(vacancy);
        vacancyIds.add(vacancy.getId());
        recordFixture("VAGA", vacancy.getId());
        opportunity.setVagaVoluntariado(vacancy);
        return opportunity;
    }

    private Integer createPending(Vaga target, Usuario applicant) throws IOException {
        CandidaturasVoluntariado application = new CandidaturasVoluntariado();
        application.setVaga(target);
        application.setUsuario(applicant);
        application.setStatus(StatusCandidatura.PENDENTE);
        application.setDataCandidatura(LocalDate.now());
        application = applications.saveAndFlush(application);
        applicationIds.add(application.getId());
        recordFixture("APPLICATION", application.getId());
        return application.getId();
    }

    private String login(UserFixture user) throws Exception {
        return accessCookies.computeIfAbsent(user.user().getId(), id -> {
            try {
                HttpResult response = request("POST", "/usuario/login",
                        Map.of("email", user.email(), "senha", PASSWORD, "lembrarDeMim", true), null, Csrf.VALID);
                if (response.status() != 200) throw new IllegalStateException("Synthetic login failed: " + response.status());
                return cookie(response, "PA_ACCESS");
            } catch (Exception failure) {
                throw new IllegalStateException("Synthetic login request failed.", failure);
            }
        });
    }


    private HttpResult request(String method, String path, Object body, String accessCookie, Csrf csrf) throws Exception {
        return send(prepare(method, path, body, accessCookie, csrf));
    }

    private PreparedRequest prepare(String method, String path, Object body, String accessCookie, Csrf csrfMode)
            throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(baseUrl() + path)).header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header("X-Real-IP", ip);
        List<String> cookies = new ArrayList<>();
        if (csrfMode != Csrf.NONE) {
            var csrfResponse = http.send(java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create(baseUrl() + "/usuario/csrf")).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            JsonNode csrf = objectMapper.readTree(csrfResponse.body());
            String csrfCookie = csrfResponse.headers().allValues("Set-Cookie").stream()
                    .map(value -> value.split(";", 2)[0])
                    .filter(value -> value.startsWith("XSRF-TOKEN="))
                    .findFirst().orElseThrow();
            cookies.add(csrfCookie);
            if (csrfMode == Csrf.VALID) {
                builder.header(csrf.path("headerName").asText(), csrf.path("token").asText());
            } else if (csrfMode == Csrf.INVALID) {
                builder.header(csrf.path("headerName").asText(), "synthetic-invalid-csrf");
            }
        }
        if (accessCookie != null) cookies.add(accessCookie);
        if (!cookies.isEmpty()) builder.header("Cookie", String.join("; ", cookies));
        if (body == null) {
            builder.method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        }
        return new PreparedRequest(builder.build());
    }

    private HttpResult send(PreparedRequest request) throws Exception {
        var response = http.send(request.request(), java.net.http.HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.headers().allValues("Set-Cookie"), response.body());
    }

    private List<HttpResult> simultaneously(PreparedRequest first, PreparedRequest second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<HttpResult> one = pool.submit(() -> { ready.countDown(); assertTrue(start.await(15, TimeUnit.SECONDS)); return send(first); });
            Future<HttpResult> two = pool.submit(() -> { ready.countDown(); assertTrue(start.await(15, TimeUnit.SECONDS)); return send(second); });
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(45, TimeUnit.SECONDS), two.get(45, TimeUnit.SECONDS));
        }
    }

    private boolean awaitPostgresOrganizationLockWait() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject("""
                    select exists (
                        select 1
                        from pg_stat_activity
                        where datname = current_database()
                          and pid <> pg_backend_pid()
                          and wait_event_type = 'Lock'
                          and query ilike '%oscs%'
                    )
                    """, Boolean.class);
            if (Boolean.TRUE.equals(waiting)) return true;
            Thread.sleep(25);
        }
        return false;
    }

    private Integer applicationId(HttpResult response) throws IOException {
        Integer id = objectMapper.readTree(response.body()).path("id").asInt();
        assertTrue(id > 0);
        applicationIds.add(id);
        recordFixture("APPLICATION", id);
        return id;
    }

    private long approvedCount(Vaga target) {
        return applications.countByVaga_IdAndStatus(target.getId(), StatusCandidatura.APROVADA);
    }

    private long countOrganizationStatusAudits(Integer actorId) {
        Long count = jdbc.queryForObject("""
                select count(*) from admin_operation_audit_events
                 where actor_user_id_snapshot = ?
                   and action = 'ORGANIZATION_STATUS_CHANGED'
                   and target_type = 'OSC' and target_id = ?
                """, Long.class, actorId, osc.getId().toString());
        return count == null ? 0 : count;
    }

    private AdminFixture createPlatformAdminSession() throws IOException {
        UserFixture fixture = createUser(roles.findByNome("USUARIO").orElseThrow(), "platform-admin-status");
        Usuario admin = fixture.user();
        admin.setTelefone("+5511999999999");
        admin.setTelefoneVerificado(true);
        admin.getRoles().add(roles.findByNome("ADMIN").orElseThrow());
        admin = users.saveAndFlush(admin);

        Instant now = Instant.now();
        UUID assignmentId = UUID.randomUUID();
        adminAssignments.insert(new AdminAssignment(assignmentId, admin.getId(), admin.getId(),
                AdminProfile.PLATFORM_ADMIN, AdminAssignmentStatus.ACTIVE, now, admin.getId(),
                now, admin.getId(), null, "Synthetic integration fixture"));
        adminAssignmentIds.add(assignmentId);
        recordFixtureIdentifier("ADMIN_ASSIGNMENT", assignmentId.toString());

        UUID challengeId = UUID.randomUUID();
        AdminAuthChallenge challenge = new AdminAuthChallenge();
        challenge.setId(challengeId);
        challenge.setUsuario(admin);
        challenge.setAccountKeyHash(abuseKey.accountKey(fixture.email()));
        challenge.setIpKeyHash(abuseKey.ipKey(ip));
        challenge.setEmailSnapshotHash(abuseKey.accountKey(fixture.email()));
        challenge.setPhoneSnapshotHash(abuseKey.phoneKey(admin.getTelefone()));
        challenge.setState(AdminAuthChallenge.State.VERIFIED);
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusSeconds(600));
        challenge.setEmailVerifiedAt(now);
        challenge.setSmsVerifiedAt(now);
        challenge.setCompletedAt(now);
        adminChallenges.saveAndFlush(challenge);
        adminChallengeIds.add(challengeId);
        recordFixtureIdentifier("ADMIN_CHALLENGE", challengeId.toString());

        String rawCredential = adminPrivilegedSessions.emitir(challengeId, ip).rawCredential();
        UUID sessionId = jdbc.queryForObject(
                "select id from admin_privileged_sessions where challenge_id = ?", UUID.class, challengeId);
        adminSessionIds.add(sessionId);
        recordFixtureIdentifier("ADMIN_SESSION", sessionId.toString());
        UUID authAuditId = jdbc.queryForObject("""
                select id from admin_security_audit_events
                 where challenge_id = ? and event_type = 'ADMIN_SESSION_ISSUED'
                 order by occurred_at desc limit 1
                """, UUID.class, challengeId);
        recordFixtureIdentifier("ADMIN_AUTH_AUDIT", authAuditId.toString());
        return new AdminFixture(admin, rawCredential);
    }

    private JsonNode json(HttpResult response) throws IOException {
        return objectMapper.readTree(response.body());
    }

    private String applyPath(Vaga target) {
        return "/voluntariado/vagas/" + target.getId() + "/candidaturas";
    }

    private String availabilityPath(Vaga target) {
        return "/voluntariado/vagas/" + target.getId() + "/disponibilidade";
    }

    private String decisionPath(Osc organization, Integer applicationId) {
        return "/osc/" + organization.getId() + "/candidaturas/" + applicationId;
    }

    private Map<String, Object> updatePostBody(Post current, int capacity) {
        Vaga currentVaga = current.getVagaVoluntariado();
        Map<String, Object> vacancyBody = Map.ofEntries(
                Map.entry("quantidadeVagas", capacity),
                Map.entry("modalidadeVoluntariado", currentVaga.getModalidade().name()),
                Map.entry("requisitos", currentVaga.getRequisitos()),
                Map.entry("cargaHoraria", currentVaga.getCargaHoraria()),
                Map.entry("cidade", currentVaga.getCidade()),
                Map.entry("bairro", currentVaga.getBairro()),
                Map.entry("rua", currentVaga.getRua()),
                Map.entry("numero", currentVaga.getNumero()),
                Map.entry("complemento", ""),
                Map.entry("dataInicio", currentVaga.getDataInicio().toString()),
                Map.entry("dataFim", currentVaga.getDataFim().toString()),
                Map.entry("status", currentVaga.getStatus().name()));
        return Map.of("id", current.getId(),
                "post", Map.of("tipo", "VOLUNTARIADO", "ativo", current.isAtivo(),
                        "titulo", current.getTitulo(), "conteudo", current.getConteudo()),
                "vaga", vacancyBody);
    }

    private String cookie(HttpResult response, String name) {
        return response.cookies().stream().map(value -> value.split(";", 2)[0])
                .filter(value -> value.startsWith(name + "="))
                .map(value -> value.substring(name.length() + 1)).findFirst()
                .map(value -> name + "=" + value).orElseThrow();
    }

    private String baseUrl() { return "http://127.0.0.1:" + port; }

    private void recordFixture(String kind, Integer id) throws IOException {
        recordFixtureIdentifier(kind, id.toString());
    }

    private void recordFixtureIdentifier(String kind, String id) throws IOException {
        java.nio.file.Path path = java.nio.file.Path.of(ledger);
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, kind + ":" + id + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private String validCpf() {
        SecureRandom random = new SecureRandom();
        while (true) {
            int[] digits = new int[11];
            for (int i = 0; i < 9; i++) digits[i] = random.nextInt(10);
            for (int position = 9; position < 11; position++) {
                int sum = 0;
                for (int i = 0; i < position; i++) sum += digits[i] * (position + 1 - i);
                int remainder = (sum * 10) % 11;
                digits[position] = remainder == 10 ? 0 : remainder;
            }
            String value = java.util.Arrays.stream(digits).mapToObj(String::valueOf)
                    .collect(java.util.stream.Collectors.joining());
            try { return new Cpf(value).valor(); } catch (IllegalArgumentException ignored) { }
        }
    }

    private String validCnpj() {
        SecureRandom random = new SecureRandom();
        while (true) {
            int[] digits = new int[14];
            for (int i = 0; i < 12; i++) digits[i] = random.nextInt(10);
            int[] firstWeights = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            int sum = 0;
            for (int i = 0; i < 12; i++) sum += digits[i] * firstWeights[i];
            digits[12] = sum % 11 < 2 ? 0 : 11 - sum % 11;
            int[] secondWeights = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            sum = 0;
            for (int i = 0; i < 13; i++) sum += digits[i] * secondWeights[i];
            digits[13] = sum % 11 < 2 ? 0 : 11 - sum % 11;
            String value = java.util.Arrays.stream(digits).mapToObj(String::valueOf)
                    .collect(java.util.stream.Collectors.joining());
            try { return new Cnpj(value).getCnpj(); } catch (IllegalArgumentException ignored) { }
        }
    }

    private record UserFixture(Usuario user, String email) { }
    private record AdminFixture(Usuario user, String rawCredential) { }
    private record HttpResult(int status, List<String> cookies, String body) { }
    private record PreparedRequest(java.net.http.HttpRequest request) { }
    private enum Csrf { NONE, INVALID, VALID }
}
