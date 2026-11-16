package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.admin.AdminAssignment;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentStatus;
import com.portoajuda.aplicacao_osc.admin.AdminProfile;
import com.portoajuda.aplicacao_osc.controller.AdminSessionCookie;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.Genero;
import com.portoajuda.aplicacao_osc.enums.ModalidadeVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.AdminPrivilegedSessionRepository;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import com.portoajuda.aplicacao_osc.repository.SessaoUsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.ContentReportJdbcRepository;
import com.portoajuda.aplicacao_osc.service.AdminAuthCodeSecurity;
import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Real JDBC/HTTP tests. Disabled until the operator confirms the exact Supabase TEST host. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "server.port=0",
        "app.cookies.secure=false",
        "ALLOWED_ORIGINS=http://localhost:8080",
        "RAILWAY_PUBLIC_EDGE_IP_HEADER_ENABLED=true",
        "app.frontend-url=http://localhost:8080",
        "app.backend-url=http://localhost:8080",
        "RESEND_API_KEY=re_test_content_reporting_mock_only",
        "MAIL_FROM=tests@example.invalid",
        "supabase.url=https://storage.example.invalid",
        "supabase.secret-key=synthetic-storage-key",
        "supabase.bucket-publico=synthetic",
        "STRIPE_SECRET_KEY=sk_test_content_reporting_mock_only",
        "JWT_SECRET_KEY=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU2Nzg5",
        "admin-auth.code-pepper=synthetic-content-report-admin-hmac-key-minimum-32-bytes",
        "app.password-recovery.pepper=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.maximum-pool-size=16",
        "app.initialization.seed-roles=false",
        "app.maintenance.cleanup-cron=-",
        "app.admin-auth.controls-cleanup-cron=-",
        "app.admin.rbac.enforcement-enabled=true",
        "app.content-reporting.enabled=true",
        "app.content-reporting.publication-threshold=5",
        "app.content-reporting.volunteer-threshold=5",
        "app.content-reporting.organization-threshold=10",
        "app.content-reporting.publication-window-hours=24",
        "app.content-reporting.volunteer-window-hours=24",
        "app.content-reporting.organization-window-hours=48"
})
@ExtendWith(ContentReportingTestDatabaseCondition.class)
class ContentReportPostgresIntegrationTest {
    private static final String PASSWORD = "SyntheticContentQA7!";
    private final java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository users;
    @Autowired RoleRepository roles;
    @Autowired OscRepository oscs;
    @Autowired PostRepository posts;
    @Autowired VagaRepository vacancies;
    @Autowired OscMembrosRepository members;
    @Autowired CandidaturasVoluntariadoRepository applications;
    @Autowired SessaoUsuarioRepository userSessions;
    @Autowired AdminAuthChallengeRepository adminChallenges;
    @Autowired AdminPrivilegedSessionRepository adminSessions;
    @Autowired AdminAssignmentRepository adminAssignments;
    @Autowired AdminPrivilegedSessionService adminPrivilegedSessions;
    @Autowired AdminAuthCodeSecurity authCrypto;
    @Autowired PasswordEncoder encoder;
    @Autowired ContentReportJdbcRepository reportRows;
    @Autowired PlatformTransactionManager transactionManager;
    @LocalServerPort int port;

    @MockitoBean EmailService emailService;
    @MockitoBean SmsService smsService;
    @MockitoBean AdminAuthDeliveryWorker adminDeliveryWorker;
    @MockitoBean SupabaseStorageService storageService;

    private final Set<Integer> userIds = new HashSet<>();
    private final Set<Integer> oscIds = new HashSet<>();
    private final Set<Integer> postIds = new HashSet<>();
    private final Set<Integer> vacancyIds = new HashSet<>();
    private final Set<Integer> retainedOscs = new HashSet<>();
    private final Set<Integer> retainedPosts = new HashSet<>();
    private final Set<Integer> retainedUsers = new HashSet<>();
    private final Set<UUID> assignmentIds = new HashSet<>();
    private final Set<UUID> challengeIds = new HashSet<>();
    private final Set<UUID> adminSessionIds = new HashSet<>();
    private final Set<Long> reportIds = new HashSet<>();
    private final Set<Long> blockIds = new HashSet<>();
    private final Set<Long> reviewIds = new HashSet<>();
    private final java.util.Map<Integer, String> accessCookies = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, UserFixture> userFixtures = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, TipoPost> postTypes = new java.util.concurrent.ConcurrentHashMap<>();
    @Value("${spring.jpa.hibernate.ddl-auto}") String ddlAuto;
    @Value("${app.content-reporting.enabled}") boolean reportingEnabled;

    private String ledger;
    private String ip;
    private boolean databaseConfirmed;
    private UserFixture creator;
    private Osc osc;
    private Post publication;
    private Post volunteerPost;
    private Vaga vacancy;

    @BeforeEach
    void establishConfirmedTestTargetAndCreateOnlySyntheticRows() throws Exception {
        String url;
        String sslMode;
        try (var connection = dataSource.getConnection()) {
            url = connection.getMetaData().getURL();
            assertEquals("postgres", jdbc.queryForObject("SELECT current_database()", String.class));
            assertTrue(jdbc.queryForObject("SELECT current_user='postgres'", Boolean.class));
        }
        sslMode = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class)
                .getDataSourceProperties().getProperty("sslmode");
        String confirmedHost = System.getenv("CONTENT_REPORTING_TEST_DB_HOST");
        String actualHost = java.net.URI.create(url.substring("jdbc:".length())).getHost();
        assertEquals(confirmedHost, actualHost, "The exact TEST host must be confirmed outside the repository.");
        assertTrue(actualHost.endsWith(".supabase.com") || actualHost.endsWith(".supabase.co"));
        assertTrue(url.matches("(?i)^jdbc:postgresql://[^/]+/postgres(?:[?].*)?$"));
        assertEquals("require", sslMode);
        assertEquals("validate", ddlAuto);
        assertTrue(reportingEnabled);
        assertTrue(jdbc.queryForObject("SELECT to_regclass('public.content_reports') IS NOT NULL", Boolean.class),
                "Apply and validate the manual reporting migration before enabling this test class.");
        assertTrue(jdbc.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid='public.content_reports'::regclass", Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid='public.content_moderation_blocks'::regclass", Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid='public.content_report_review_history'::regclass", Boolean.class));
        assertNextSyntheticUserIdCannotBe47Or48();
        ledger = System.getenv("CONTENT_REPORTING_TEST_LEDGER");
        assertNotNull(ledger, "Provide a task-specific fixture ledger outside the repository.");
        Path ledgerPath = Path.of(ledger).toAbsolutePath().normalize();
        assertFalse(ledgerPath.startsWith(Path.of("").toAbsolutePath().normalize()),
                "The fixture ledger must stay outside the repository.");
        databaseConfirmed = true;
        recordFixture("RUN", UUID.randomUUID().toString());
        ip = "198.51.100." + (1 + new SecureRandom().nextInt(254));

        creator = createUser("creator", false);
        osc = createOsc(creator.user(), "Conteudo QA");
        createMembership(osc, creator.user(), CargoOsc.CRIADOR);
        publication = createPost(osc, creator.user(), TipoPost.DOACAO, false);
        volunteerPost = createPost(osc, creator.user(), TipoPost.VOLUNTARIADO, true);
        vacancy = volunteerPost.getVagaVoluntariado();
    }

    @AfterEach
    void removeOnlyThisRunFixturesAndRetainTargetsNeededByAppendOnlyHistory() throws Exception {
        if (!databaseConfirmed) return;
        retainReviewedTargetsAndHide();
        for (Integer postId : postIds) {
            if (retainedPosts.contains(postId)) continue;
            deleteTargetData("POST", postId);
            jdbc.update("DELETE FROM public.candidaturas_voluntariado WHERE id_vaga IN (SELECT id FROM public.vagas_voluntariado WHERE id_post=?)", postId);
            jdbc.update("DELETE FROM public.vagas_voluntariado WHERE id_post=?", postId);
            jdbc.update("DELETE FROM public.post WHERE id=?", postId);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.post WHERE id=?", Integer.class, postId));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.vagas_voluntariado WHERE id_post=?",Integer.class,postId));
        }
        for (Integer oscId : oscIds) {
            if (retainedOscs.contains(oscId)) continue;
            deleteTargetData("OSC", oscId);
            jdbc.update("DELETE FROM public.osc_membros WHERE id_osc=?", oscId);
            jdbc.update("DELETE FROM public.osc_causas WHERE id_osc=?", oscId);
            jdbc.update("DELETE FROM public.osc_ods WHERE id_osc=?", oscId);
            jdbc.update("DELETE FROM public.oscs WHERE id=?", oscId);
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.oscs WHERE id=?",Integer.class,oscId));
        }
        cleanupCredentialsAndUsers();
        recordFixture("CLEANUP_VERIFIED_WITH_RETAINED_AUDIT_FIXTURES", UUID.randomUUID().toString());
    }

    @Test
    void csrfIsRequiredAndAuthenticatedReporterIdentityIsNeverReturnedToReviewers() throws Exception {
        UserFixture reporter = createUser("csrf-reporter", false);
        String cookie = login(reporter);
        assertNull(publication.getVagaVoluntariado(), "Ordinary publication fixtures must not have a Vaga.");
        Map<String, Object> body = reportBody("PUBLICACAO", publication.getId(), "PRIVACY", "Fixture detail.");
        int missingCsrfStatus = request("POST", "/denuncias", body, cookie, Csrf.NONE).status();
        int invalidCsrfStatus = request("POST", "/denuncias", body, cookie, Csrf.INVALID).status();
        assertTrue(missingCsrfStatus == 401 || missingCsrfStatus == 403,
                "The real filter chain must reject a mutation without a CSRF token.");
        assertTrue(invalidCsrfStatus == 401 || invalidCsrfStatus == 403,
                "The real filter chain must reject a mutation with an invalid CSRF token.");
        assertEquals(0, reportCount("POST", publication.getId(), "PENDING"));
        assertEquals(201, request("POST", "/denuncias", body, cookie, Csrf.VALID).status());
        recordTargetArtifacts("POST",publication.getId());

        AdminFixture moderator = createAdminSession(AdminProfile.MODERATOR, "report-moderator");
        HttpResult queue = request("GET", "/admin/denuncias?tipo=POST&estado=PENDING", null,
                moderator.cookie(), Csrf.NONE);
        assertEquals(200, queue.status());
        assertFalse(queue.body().contains(reporter.email()));
        assertFalse(queue.body().contains("reporter_key"));
        assertFalse(queue.body().contains("reporterKey"));
        assertFalse(queue.body().contains("reporter_user_id"));
        HttpResult detail = request("GET", "/admin/denuncias/POST/" + publication.getId(), null,
                moderator.cookie(), Csrf.NONE);
        assertEquals(200, detail.status());
        assertFalse(detail.body().contains(reporter.email()));

        UserFixture roleAdminWithoutSession = createUser("role-admin-only", true);
        HttpResult commonCookie = request("GET", "/admin/denuncias", null,
                login(roleAdminWithoutSession), Csrf.NONE);
        assertTrue(commonCookie.status() == 401 || commonCookie.status() == 403,
                "An ordinary ROLE_ADMIN JWT without ADMIN_SESSION must not enter the reporting queue.");
    }

    @Test
    void publicOrganizationListingReturnsPagedContractAndExcludesBlockedAndInactiveOrganizations() throws Exception {
        HttpResult first = request("GET", "/osc/list?page=0&size=6", null, null, Csrf.NONE);
        assertEquals(200, first.status());
        JsonNode firstPage = json(first);
        assertTrue(firstPage.path("content").isArray());
        assertTrue(firstPage.path("totalElements").isIntegralNumber());
        assertTrue(firstPage.path("totalPages").isIntegralNumber());
        assertTrue(firstPage.path("content").size() <= 6);
        assertTrue(containsOrganization(firstPage, osc.getId()), "Newest synthetic active NGO should be listed.");
        long totalBeforeBlock = firstPage.path("totalElements").asLong();

        Osc inactive = createOsc(creator.user(), "listing-inactive");
        inactive.setStatus(StatusOsc.INATIVA);
        oscs.saveAndFlush(inactive);
        assertEquals(404, request("GET", "/osc/" + inactive.getId(), null, null, Csrf.NONE).status());

        insertSyntheticBlock("OSC", osc.getId(), "OSC", "ADMIN_RESTRICTION");
        HttpResult afterBlock = request("GET", "/osc/list?page=0&size=6", null, null, Csrf.NONE);
        assertEquals(200, afterBlock.status());
        JsonNode visiblePage = json(afterBlock);
        assertFalse(containsOrganization(visiblePage, osc.getId()));
        assertEquals(totalBeforeBlock - 1, visiblePage.path("totalElements").asLong(),
                "The count query must apply the same block visibility as page content.");
        assertEquals(404, request("GET", "/osc/" + osc.getId(), null, null, Csrf.NONE).status());

        HttpResult second = request("GET", "/osc/list?page=1&size=6", null, null, Csrf.NONE);
        assertEquals(200, second.status());
        assertEquals(visiblePage.path("totalElements").asLong(), json(second).path("totalElements").asLong());
        HttpResult emptyPage = request("GET", "/osc/list?page=999999&size=6", null, null, Csrf.NONE);
        assertEquals(200, emptyPage.status());
        assertEquals(0, json(emptyPage).path("content").size());
        assertEquals(visiblePage.path("totalElements").asLong(), json(emptyPage).path("totalElements").asLong());
        recordTargetArtifacts("OSC", osc.getId());
    }

    @Test
    void reportHttpContractRejectsMissingBlankAndOverLimitDescriptions() throws Exception {
        UserFixture reporter = createUser("required-report-description", false);
        String cookie = login(reporter);
        Map<String, Object> missing = new java.util.HashMap<>();
        missing.put("tipo", "PUBLICACAO");
        missing.put("alvoId", publication.getId());
        missing.put("motivo", "OTHER");
        assertEquals(400, request("POST", "/denuncias", missing, cookie, Csrf.VALID).status());
        for (String description : List.of("", " \t\n ", "x".repeat(1001))) {
            assertEquals(400, request("POST", "/denuncias",
                    reportBody("PUBLICACAO", publication.getId(), "OTHER", description), cookie, Csrf.VALID).status());
        }
        assertEquals(0, reportCount("POST", publication.getId(), "PENDING"));
    }

    private static boolean containsOrganization(JsonNode page, int id) {
        for (JsonNode organization : page.path("content")) if (organization.path("id").asInt() == id) return true;
        return false;
    }

    @Test
    void reportResolutionAndBlockClosureRemainAllowedButPersistedIdentityCannotChange() throws Exception {
        UserFixture reporter = createUser("identity-reporter", false);
        assertEquals(201, submitReport(reporter, "PUBLICACAO", publication.getId()).status());
        insertSyntheticBlock("POST", publication.getId(), "PUBLICATION", "AUTOMATIC_PROTECTION");
        AdminFixture moderator = createAdminSession(AdminProfile.MODERATOR, "identity-reviewer");

        assertEquals(204, request("POST", "/admin/denuncias/POST/" + publication.getId() + "/decisao",
                Map.of("decisao", "DISMISS", "justificativa", "Close synthetic identity test."),
                moderator.cookie(), Csrf.VALID).status());
        assertEquals(1, reportCount("POST", publication.getId(), "DISMISSED"));
        assertEquals(1, blockCount("POST", publication.getId(), "AUTOMATIC_PROTECTION", true),
                "Dismissing reports must not silently lift automatic protection.");
        assertEquals(0, blockCount("POST", publication.getId(), "ADMIN_RESTRICTION", true));

        for (String mutation : List.of(
                "target_type='OSC'", "target_id=target_id+100000", "content_kind='VOLUNTEERING'",
                "reporter_key=repeat('a',64)", "created_at=created_at+INTERVAL '1 second'")) {
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE public.content_reports SET " + mutation + " WHERE target_type='POST' AND target_id=?",
                    publication.getId()), "Report identity mutation should be rejected: " + mutation);
        }
        for (String mutation : List.of(
                "target_type='OSC'", "target_id=target_id+100000", "content_kind='VOLUNTEERING'",
                "source='ADMIN_RESTRICTION'", "reason='Changed after moderation'")) {
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE public.content_moderation_blocks SET " + mutation
                            + " WHERE target_type='POST' AND target_id=? AND source='AUTOMATIC_PROTECTION'",
                    publication.getId()), "Moderation-block identity mutation should be rejected: " + mutation);
        }

        // Non-identity edits remain permitted for a post with report history.
        assertEquals(1, jdbc.update("UPDATE public.post SET titulo='Edited while preserving report history' WHERE id=?",
                publication.getId()));
        recordTargetArtifacts("POST", publication.getId());
    }

    @Test
    void upheldPointsOnlyPreserveExistingAutomaticProtectionAndHideChoiceAddsAnIndependentBlock() throws Exception {
        UserFixture reporter=createUser("separate-points-report",false);
        assertEquals(201,submitReport(reporter,"PUBLICACAO",publication.getId()).status());
        insertSyntheticBlock("POST",publication.getId(),"PUBLICATION","AUTOMATIC_PROTECTION");
        AdminFixture platform=createAdminSession(AdminProfile.PLATFORM_ADMIN,"separate-points-reviewer");
        assertEquals(204,request("POST","/admin/denuncias/POST/"+publication.getId()+"/decisao",
                Map.of("decisao","UPHOLD","pontos",2,"restringirVisibilidade",false,
                        "justificativa","Synthetic points without new visibility restriction."),platform.cookie(),Csrf.VALID).status());
        assertEquals(1,blockCount("POST",publication.getId(),"AUTOMATIC_PROTECTION",true));
        assertEquals(0,blockCount("POST",publication.getId(),"ADMIN_RESTRICTION",true));
        assertEquals(2,jdbc.queryForObject("SELECT coalesce(sum(points_delta),0)::bigint FROM public.moderation_point_ledger WHERE subject_user_id=?",Long.class,creator.user().getId()));
        assertEquals(0,jdbc.queryForObject("SELECT coalesce(sum(points_delta),0)::bigint FROM public.moderation_point_ledger WHERE subject_osc_id=?",Long.class,osc.getId()));

        Post explicitlyHidden=createPost(osc,creator.user(),TipoPost.DOACAO,false);
        assertEquals(201,submitReport(reporter,"PUBLICACAO",explicitlyHidden.getId()).status());
        insertSyntheticBlock("POST",explicitlyHidden.getId(),"PUBLICATION","AUTOMATIC_PROTECTION");
        assertEquals(204,request("POST","/admin/denuncias/POST/"+explicitlyHidden.getId()+"/decisao",
                Map.of("decisao","UPHOLD","pontos",1,"restringirVisibilidade",true,
                        "justificativa","Synthetic upheld decision with explicit visibility restriction."),platform.cookie(),Csrf.VALID).status());
        assertEquals(1,blockCount("POST",explicitlyHidden.getId(),"AUTOMATIC_PROTECTION",true));
        assertEquals(1,blockCount("POST",explicitlyHidden.getId(),"ADMIN_RESTRICTION",true));
        assertEquals(3,jdbc.queryForObject("SELECT coalesce(sum(points_delta),0)::bigint FROM public.moderation_point_ledger WHERE subject_user_id=?",Long.class,creator.user().getId()));
        recordTargetArtifacts("POST",publication.getId());
        recordTargetArtifacts("POST",explicitlyHidden.getId());
    }

    @Test
    void preventiveHideAndExplicitRestoreDoNotResolveReportsOrChangePointsOrOwnerStatus() throws Exception {
        UserFixture reporter=createUser("preventive-hide-report",false);
        Post target=createPost(osc,creator.user(),TipoPost.DOACAO,false);
        assertEquals(201,submitReport(reporter,"PUBLICACAO",target.getId()).status());
        AdminFixture moderator=createAdminSession(AdminProfile.MODERATOR,"preventive-hide-moderator");
        assertEquals(204,request("POST","/admin/denuncias/POST/"+target.getId()+"/ocultacao-preventiva",
                Map.of("justificativa","Synthetic preventive visibility hold while reports are reviewed."),moderator.cookie(),Csrf.VALID).status());
        assertEquals(1,reportCount("POST",target.getId(),"PENDING"));
        assertEquals(0,reviewHistoryCount("POST",target.getId()));
        assertEquals(1,blockCount("POST",target.getId(),"ADMIN_RESTRICTION",true));
        assertEquals(0,jdbc.queryForObject("SELECT coalesce(sum(points_delta),0)::bigint FROM public.moderation_point_ledger WHERE subject_user_id=?",Long.class,creator.user().getId()));

        insertSyntheticBlock("POST",target.getId(),"PUBLICATION","AUTOMATIC_PROTECTION");
        Long manualBlock=activeBlockId("POST",target.getId(),"ADMIN_RESTRICTION");
        Long automaticBlock=activeBlockId("POST",target.getId(),"AUTOMATIC_PROTECTION");
        AdminFixture platform=createAdminSession(AdminProfile.PLATFORM_ADMIN,"preventive-hide-restorer");
        assertEquals(204,request("POST","/admin/denuncias/POST/"+target.getId()+"/bloqueios/"+manualBlock+"/restauracao",
                Map.of("justificativa","Synthetic restoration selects only the administrative hold."),platform.cookie(),Csrf.VALID).status());
        assertEquals(0,blockCount("POST",target.getId(),"ADMIN_RESTRICTION",true));
        assertEquals(1,blockCount("POST",target.getId(),"AUTOMATIC_PROTECTION",true));
        assertEquals(1,reportCount("POST",target.getId(),"PENDING"));
        assertEquals(0,reviewHistoryCountForDecision("POST",target.getId(),"UPHOLD"));

        assertEquals(204,request("POST","/admin/denuncias/POST/"+target.getId()+"/decisao",
                Map.of("decisao","DISMISS","justificativa","Synthetic dismissal preserves automatic protection."),moderator.cookie(),Csrf.VALID).status());
        assertEquals(1,blockCount("POST",target.getId(),"AUTOMATIC_PROTECTION",true));
        assertEquals(204,request("POST","/admin/denuncias/POST/"+target.getId()+"/bloqueios/"+automaticBlock+"/restauracao",
                Map.of("justificativa","Synthetic explicit restoration of the automatic block."),platform.cookie(),Csrf.VALID).status());
        assertEquals(0,blockCount("POST",target.getId(),"AUTOMATIC_PROTECTION",true));
        assertEquals(0,jdbc.queryForObject("SELECT coalesce(sum(points_delta),0)::bigint FROM public.moderation_point_ledger WHERE subject_user_id=?",Long.class,creator.user().getId()));
        assertEquals(StatusOsc.ATIVA,oscs.findById(osc.getId()).orElseThrow().getStatus());
        recordTargetArtifacts("POST",target.getId());
    }

    @Test
    void incompatiblePostTypeChangeIsRejectedAfterReportHistoryWhileOrdinaryPostEditsRemainAllowed() throws Exception {
        UserFixture reporter = createUser("type-guard-reporter", false);
        assertEquals(201, submitReport(reporter, "PUBLICACAO", publication.getId()).status());

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE public.post SET tipo='VOLUNTARIADO' WHERE id=?", publication.getId()));
        assertEquals(1, jdbc.update("UPDATE public.post SET conteudo='Ordinary edit remains valid.' WHERE id=?",
                publication.getId()));
        assertEquals(TipoPost.DOACAO, posts.findById(publication.getId()).orElseThrow().getTipo());
        recordTargetArtifacts("POST", publication.getId());
    }

    @Test
    void concurrentPostTypeChangeAndReportSerializeWithoutContradictoryHistory() throws Exception {
        String reporterKey = UUID.randomUUID().toString().replace("-", "")
                + "00000000000000000000000000000000";
        assertEquals(64, reporterKey.length());
        CountDownLatch reportInserted = new CountDownLatch(1);
        CountDownLatch allowReportCommit = new CountDownLatch(1);
        CountDownLatch typeChangeStarted = new CountDownLatch(1);
        AtomicInteger reporterPid = new AtomicInteger();
        AtomicInteger typeChangerPid = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> reporting = executor.submit(() -> {
                try (var connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        try (var pid = connection.prepareStatement("SELECT pg_backend_pid()")) {
                            try (var result = pid.executeQuery()) { result.next(); reporterPid.set(result.getInt(1)); }
                        }
                        try (var insert = connection.prepareStatement("""
                                INSERT INTO public.content_reports(target_type,target_id,content_kind,reporter_key,reason)
                                VALUES ('POST',?,'PUBLICATION',?,'OTHER')
                                """)) {
                            insert.setInt(1, publication.getId());
                            insert.setString(2, reporterKey);
                            insert.executeUpdate();
                        }
                        reportInserted.countDown();
                        assertTrue(allowReportCommit.await(10, TimeUnit.SECONDS));
                        connection.commit();
                    } catch (Throwable failure) {
                        connection.rollback();
                        throw new RuntimeException(failure);
                    }
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });

            Future<Throwable> typeChange = executor.submit(() -> {
                assertTrue(reportInserted.await(10, TimeUnit.SECONDS));
                try (var connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        try (var pid = connection.prepareStatement("SELECT pg_backend_pid()")) {
                            try (var result = pid.executeQuery()) { result.next(); typeChangerPid.set(result.getInt(1)); }
                        }
                        typeChangeStarted.countDown();
                        try (var update = connection.prepareStatement("UPDATE public.post SET tipo='VOLUNTARIADO' WHERE id=?")) {
                            update.setInt(1, publication.getId());
                            update.executeUpdate();
                        }
                        connection.commit();
                        return null;
                    } catch (Throwable failure) {
                        connection.rollback();
                        return failure;
                    }
                }
            });

            try {
                assertTrue(reportInserted.await(10, TimeUnit.SECONDS));
                assertTrue(typeChangeStarted.await(10, TimeUnit.SECONDS));
                awaitDatabaseBlocker(typeChangerPid.get(), reporterPid.get());
                allowReportCommit.countDown();
                reporting.get(15, TimeUnit.SECONDS);
                Throwable rejected = typeChange.get(15, TimeUnit.SECONDS);
                assertNotNull(rejected, "Incompatible type change must fail after the report commits.");
                assertEquals(TipoPost.DOACAO, posts.findById(publication.getId()).orElseThrow().getTipo());
                assertEquals(1, reportCount("POST", publication.getId(), "PENDING"));
                recordTargetArtifacts("POST", publication.getId());
            } finally {
                allowReportCommit.countDown();
            }
        } finally {
            allowReportCommit.countDown();
        }
    }

    private void awaitDatabaseBlocker(int waitingPid, int blockingPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean blocked = jdbc.queryForObject(
                    "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blockingPid, waitingPid);
            if (Boolean.TRUE.equals(blocked)) return;
            try { Thread.sleep(25); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the deterministic PostgreSQL lock barrier.", interrupted);
            }
        }
        fail("The type-change transaction did not block behind the report's Post row lock.");
    }

    @Test
    void concurrentDuplicateReportsCreateExactlyOnePendingRow() throws Exception {
        UserFixture reporter = createUser("duplicate-reporter", false);
        String cookie = login(reporter);
        PreparedRequest first = prepare("POST", "/denuncias",
                reportBody("PUBLICACAO", publication.getId(), "OTHER", "Same user and target."), cookie, Csrf.VALID);
        PreparedRequest second = prepare("POST", "/denuncias",
                reportBody("PUBLICACAO", publication.getId(), "FRAUD", "Different reason does not bypass dedupe."), cookie, Csrf.VALID);
        List<HttpResult> results = simultaneously(first, second);
        assertEquals(1, results.stream().filter(r -> r.status()==201).count());
        assertEquals(1, results.stream().filter(r -> r.status()==409).count());
        assertEquals(1, reportCount("POST", publication.getId(), "PENDING"));
        recordTargetArtifacts("POST",publication.getId());
    }

    @Test
    void concurrentThresholdCrossingCreatesOneAutomaticBlock() throws Exception {
        List<UserFixture> reporters = new ArrayList<>();
        for (int i=0;i<6;i++) reporters.add(createUser("threshold-"+i, false));
        for (int i=0;i<4;i++) assertEquals(201, submitReport(reporters.get(i), "PUBLICACAO", publication.getId()).status());
        clearInvocations(emailService);
        PreparedRequest fifth = prepare("POST", "/denuncias",
                reportBody("PUBLICACAO", publication.getId(), "OTHER", "Concurrent threshold report."),
                login(reporters.get(4)), Csrf.VALID);
        PreparedRequest sixth = prepare("POST", "/denuncias",
                reportBody("PUBLICACAO", publication.getId(), "OTHER", "Concurrent threshold report."),
                login(reporters.get(5)), Csrf.VALID);
        List<HttpResult> results = simultaneously(fifth, sixth);
        assertEquals(List.of(201,201), results.stream().map(HttpResult::status).sorted().toList());
        assertEquals(6, reportCount("POST", publication.getId(), "PENDING"));
        assertEquals(1, blockCount("POST", publication.getId(), "AUTOMATIC_PROTECTION", true));
        verify(emailService, times(1)).enviarAvisoProtecaoConteudo(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        recordTargetArtifacts("POST",publication.getId());
        JsonNode publicPosts = json(request("GET", "/post/osc/"+osc.getId()+"/publicos", null, null, Csrf.NONE));
        assertFalse(containsId(publicPosts, publication.getId()));
    }

    @Test
    void organizationProtectionNeedsTenDistinctPendingReportersInFortyEightHours() throws Exception {
        List<UserFixture> reporters=new ArrayList<>();
        for(int i=0;i<10;i++) reporters.add(createUser("organization-threshold-"+i,false));
        for(int i=0;i<9;i++) assertEquals(201,submitReport(reporters.get(i),"OSC",osc.getId()).status());
        assertEquals(9,reportRows.qualifyingCount("OSC",osc.getId(),"OSC",48));
        assertEquals(0,blockCount("OSC",osc.getId(),"AUTOMATIC_PROTECTION",true));
        assertEquals(201,submitReport(reporters.get(9),"OSC",osc.getId()).status());
        assertEquals(10,reportRows.qualifyingCount("OSC",osc.getId(),"OSC",48));
        assertEquals(1,blockCount("OSC",osc.getId(),"AUTOMATIC_PROTECTION",true));
        assertEquals(404,request("GET","/osc/"+osc.getId(),null,null,Csrf.NONE).status());
    }

    @Test
    void rollingWindowUsesDatabaseTimeAndExcludesExpiredReports() throws Exception {
        for (int i=1;i<=4;i++) insertSyntheticPendingReportAt(publication.getId(), i, 24, 1);
        assertEquals(0, reportRows.qualifyingCount("POST",publication.getId(),"PUBLICATION",24));
        Long exactlyAtBoundaryAndThreshold = new TransactionTemplate(transactionManager).execute(status -> {
            insertSyntheticPendingReportAt(publication.getId(), 5, 24, 0);
            for (int i=6;i<=9;i++) insertSyntheticPendingReportAt(publication.getId(), i, 0, 0);
            return reportRows.qualifyingCount("POST",publication.getId(),"PUBLICATION",24);
        });
        assertEquals(5L, exactlyAtBoundaryAndThreshold);
        assertEquals(0, blockCount("POST",publication.getId(),"AUTOMATIC_PROTECTION",true));
    }

    @Test
    void dismissedAndUpheldReportsBothStartCooldownAndReapplicationPreservesHistory() throws Exception {
        UserFixture reporter = createUser("cooldown-outcomes", false);
        assertEquals(201, submitReport(reporter, "PUBLICACAO", publication.getId()).status());
        AdminFixture moderator=createAdminSession(AdminProfile.MODERATOR,"review-race-moderator");
        assertEquals(204, request("POST", "/admin/denuncias/POST/" + publication.getId() + "/decisao",
                Map.of("decisao", "DISMISS", "justificativa", "Synthetic dismissed cooldown."),
                moderator.cookie(), Csrf.VALID).status());
        assertEquals(409, submitReport(reporter, "PUBLICACAO", publication.getId()).status());
        assertEquals(1, reportCount("POST", publication.getId(), "DISMISSED"));

        Post upheldPost = createPost(osc, creator.user(), TipoPost.DOACAO, false);
        assertEquals(201, submitReport(reporter, "PUBLICACAO", upheldPost.getId()).status());
        AdminFixture platform = createAdminSession(AdminProfile.PLATFORM_ADMIN, "cooldown-upheld-reviewer");
        assertEquals(204, request("POST", "/admin/denuncias/POST/" + upheldPost.getId() + "/decisao",
                Map.of("decisao", "UPHOLD", "pontos", 1, "restringirVisibilidade", false,
                        "justificativa", "Synthetic upheld cooldown."),
                platform.cookie(), Csrf.VALID).status());
        assertEquals(409, submitReport(reporter, "PUBLICACAO", upheldPost.getId()).status());
        assertEquals(1, reportCount("POST", upheldPost.getId(), "UPHELD"));
        assertEquals(1, reviewHistoryCount("POST", upheldPost.getId()));
        recordTargetArtifacts("POST", upheldPost.getId());
        recordTargetArtifacts("POST", publication.getId());
    }

    @Test
    void concurrentEligibleSubmissionsCreateOnePendingRowAndKeepTheReviewedRow() throws Exception {
        UserFixture reporter = createUser("eligible-repeat-race", false);
        assertEquals(201, submitReport(reporter, "PUBLICACAO", publication.getId()).status());
        AdminFixture moderator = createAdminSession(AdminProfile.MODERATOR, "eligible-repeat-reviewer");
        assertEquals(204, request("POST", "/admin/denuncias/POST/" + publication.getId() + "/decisao",
                Map.of("decisao", "DISMISS", "justificativa", "Synthetic old report before retry."),
                moderator.cookie(), Csrf.VALID).status());
        String reporterKey = authCrypto.hashIdentifier("content-report-reporter", String.valueOf(reporter.user().getId()));
        assertEquals(1, jdbc.update("""
                UPDATE public.content_reports
                   SET reviewed_at=CURRENT_TIMESTAMP - INTERVAL '7 days' - INTERVAL '1 second'
                 WHERE target_type='POST' AND target_id=? AND reporter_key=? AND status='DISMISSED'
                """, publication.getId(), reporterKey));
        assertEquals(1, reviewHistoryCount("POST", publication.getId()));

        String cookie = login(reporter);
        PreparedRequest first = prepare("POST", "/denuncias",
                reportBody("PUBLICACAO", publication.getId(), "OTHER", "First eligible concurrent retry."), cookie, Csrf.VALID);
        PreparedRequest second = prepare("POST", "/denuncias",
                reportBody("PUBLICACAO", publication.getId(), "FRAUD", "Second eligible concurrent retry."), cookie, Csrf.VALID);
        List<HttpResult> results = simultaneously(first, second);
        assertEquals(1, results.stream().filter(result -> result.status() == 201).count());
        assertEquals(1, results.stream().filter(result -> result.status() == 409).count());
        assertEquals(1, reportCount("POST", publication.getId(), "PENDING"));
        assertEquals(1, reportCount("POST", publication.getId(), "DISMISSED"));
        assertEquals(1, reviewHistoryCount("POST", publication.getId()));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM public.content_reports WHERE target_type='POST' AND target_id=? AND reporter_key=?",
                Integer.class, publication.getId(), reporterKey));
        recordTargetArtifacts("POST",publication.getId());
    }

    @Test
    void reviewRacingWithSubmissionCannotBypassPendingOrSevenDayCooldown() throws Exception {
        UserFixture reporter = createUser("review-submit-race", false);
        UserFixture otherReporter = createUser("review-submit-race-other", false);
        assertEquals(201, submitReport(reporter, "PUBLICACAO", publication.getId()).status());
        assertEquals(201, submitReport(otherReporter, "PUBLICACAO", publication.getId()).status());
        AdminFixture moderator = createAdminSession(AdminProfile.MODERATOR, "review-submit-race-moderator");
        PreparedRequest repeat = prepare("POST", "/denuncias",
                reportBody("PUBLICACAO", publication.getId(), "OTHER", "Retry during review race."),
                login(reporter), Csrf.VALID);
        PreparedRequest decision = prepare("POST", "/admin/denuncias/POST/" + publication.getId() + "/decisao",
                Map.of("decisao", "DISMISS", "justificativa", "Review locks before cooldown check."),
                moderator.cookie(), Csrf.VALID);

        List<HttpResult> results = simultaneously(repeat, decision);
        assertEquals(409, results.get(0).status(), "Submission must see either the pending row or its new cooldown.");
        assertEquals(204, results.get(1).status());
        assertEquals(0, reportCount("POST", publication.getId(), "PENDING"));
        assertEquals(2, reportCount("POST", publication.getId(), "DISMISSED"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM public.content_reports WHERE target_type='POST' AND target_id=?",
                Integer.class, publication.getId()));
        recordTargetArtifacts("POST", publication.getId());
    }

    @Test
    void ownerDeactivationRacesWithThresholdBlockWithoutReactivation() throws Exception {
        List<UserFixture> reporters=new ArrayList<>();
        for(int i=0;i<5;i++) reporters.add(createUser("status-race-"+i,false));
        for(int i=0;i<4;i++) assertEquals(201,submitReport(reporters.get(i),"OSC",osc.getId()).status());
        PreparedRequest finalReport=prepare("POST","/denuncias",reportBody("OSC",osc.getId(),"OTHER","Status race synthetic report."),login(reporters.get(4)),Csrf.VALID);
        PreparedRequest deactivate=prepare("PATCH","/osc/"+osc.getId()+"/status",
                Map.of("status","INATIVA","motivo","Synthetic owner status race."),login(creator),Csrf.VALID);
        List<HttpResult> results=simultaneously(finalReport,deactivate);
        assertTrue(results.get(0).status()==201 || results.get(0).status()==404);
        assertEquals(204,results.get(1).status());
        assertEquals(StatusOsc.INATIVA,oscs.findById(osc.getId()).orElseThrow().getStatus());
        assertEquals(404,request("GET","/osc/"+osc.getId(),null,null,Csrf.NONE).status());
        assertTrue(blockCount("OSC",osc.getId(),"AUTOMATIC_PROTECTION",true)<=1);
        recordTargetArtifacts("OSC",osc.getId());
        recordStatusHistory(osc.getId());
    }

    @Test
    void auditorIsReadOnlyModeratorCannotUpholdAndRestorationKeepsIndependentRestrictions() throws Exception {
        UserFixture reporter=createUser("permission-reporter",false);
        assertEquals(201,submitReport(reporter,"PUBLICACAO",publication.getId()).status());
        AdminFixture auditor=createAdminSession(AdminProfile.AUDITOR,"report-auditor");
        assertEquals(200,request("GET","/admin/denuncias?estado=PENDING",null,auditor.cookie(),Csrf.NONE).status());
        assertEquals(403,request("POST","/admin/denuncias/POST/"+publication.getId()+"/decisao",
                Map.of("decisao","DISMISS","justificativa","Auditor must remain read-only."),auditor.cookie(),Csrf.VALID).status());
        AdminFixture moderator=createAdminSession(AdminProfile.MODERATOR,"report-moderator-limit");
        assertEquals(403,request("POST","/admin/denuncias/POST/"+publication.getId()+"/decisao",
                Map.of("decisao","UPHOLD","justificativa","Moderator cannot impose restrictions."),moderator.cookie(),Csrf.VALID).status());

        assertEquals(1, jdbc.update("UPDATE public.oscs SET status='INATIVA',origem_ultima_desativacao='PORTO_AJUDA',data_arquivamento=CURRENT_TIMESTAMP WHERE id=?",
                osc.getId()));
        insertSyntheticBlock("OSC",osc.getId(),"OSC","AUTOMATIC_PROTECTION");
        insertSyntheticBlock("OSC",osc.getId(),"OSC","ADMIN_RESTRICTION");
        AdminFixture platform=createAdminSession(AdminProfile.PLATFORM_ADMIN,"report-restore-admin");
        Long manualBlockId=jdbc.queryForObject("SELECT id FROM public.content_moderation_blocks WHERE target_type='OSC' AND target_id=? AND source='ADMIN_RESTRICTION' AND active",Long.class,osc.getId());
        Long automaticBlockId=jdbc.queryForObject("SELECT id FROM public.content_moderation_blocks WHERE target_type='OSC' AND target_id=? AND source='AUTOMATIC_PROTECTION' AND active",Long.class,osc.getId());
        long pointsBefore=jdbc.queryForObject("SELECT coalesce(sum(points_delta),0)::bigint FROM public.moderation_point_ledger WHERE subject_osc_id=?",Long.class,osc.getId());
        assertEquals(400,request("POST","/admin/denuncias/OSC/"+osc.getId()+"/decisao",
                Map.of("decisao","RESTORE","justificativa","Generic restore must require a selected block."),platform.cookie(),Csrf.VALID).status());
        assertEquals(204,request("POST","/admin/denuncias/OSC/"+osc.getId()+"/bloqueios/"+manualBlockId+"/restauracao",
                Map.of("justificativa","Synthetic independent manual restriction restoration."),platform.cookie(),Csrf.VALID).status());
        assertEquals(0,blockCount("OSC",osc.getId(),"ADMIN_RESTRICTION",true));
        assertEquals(1,blockCount("OSC",osc.getId(),"AUTOMATIC_PROTECTION",true));
        assertEquals(204,request("POST","/admin/denuncias/OSC/"+osc.getId()+"/bloqueios/"+automaticBlockId+"/restauracao",
                Map.of("justificativa","Synthetic independent automatic protection restoration."),platform.cookie(),Csrf.VALID).status());
        assertEquals(0,blockCount("OSC",osc.getId(),"AUTOMATIC_PROTECTION",true));
        assertEquals(pointsBefore,jdbc.queryForObject("SELECT coalesce(sum(points_delta),0)::bigint FROM public.moderation_point_ledger WHERE subject_osc_id=?",Long.class,osc.getId()));
        assertEquals(1,reportCount("POST", publication.getId(), "PENDING"));
        Osc current=oscs.findById(osc.getId()).orElseThrow();
        assertEquals(StatusOsc.INATIVA,current.getStatus());
        assertEquals(com.portoajuda.aplicacao_osc.enums.OrigemDesativacao.PORTO_AJUDA,current.getOrigemUltimaDesativacao());
        assertNotNull(current.getDataArquivamento(),"Restoring moderation visibility must not unarchive the NGO.");
        assertEquals(404,request("GET","/osc/"+osc.getId(),null,null,Csrf.NONE).status());
        recordTargetArtifacts("OSC",osc.getId());
    }

    @Test
    void blockedVolunteerCannotBeAppliedToAndAvailabilityStaysPrivateSafe() throws Exception {
        List<UserFixture> reporters=new ArrayList<>();
        for(int i=0;i<5;i++) reporters.add(createUser("volunteer-block-"+i,false));
        for(UserFixture reporter:reporters) assertEquals(201,submitReport(reporter,"VOLUNTARIADO",volunteerPost.getId()).status());
        JsonNode availability=json(request("GET","/voluntariado/vagas/"+vacancy.getId()+"/disponibilidade",null,null,Csrf.NONE));
        assertFalse(availability.path("aceitaCandidaturas").asBoolean());
        UserFixture applicant=createUser("blocked-applicant",false);
        assertEquals(409,request("POST","/voluntariado/vagas/"+vacancy.getId()+"/candidaturas",
                Map.of("mensagem","Synthetic blocked application."),login(applicant),Csrf.VALID).status());
        JsonNode publicContent=json(request("GET","/post/osc/"+osc.getId()+"/publicos",null,null,Csrf.NONE));
        assertFalse(containsId(publicContent,volunteerPost.getId()));
    }

    private HttpResult submitReport(UserFixture reporter,String type,Integer targetId) throws Exception {
        HttpResult response=request("POST","/denuncias",reportBody(type,targetId,"OTHER","Synthetic report context."),login(reporter),Csrf.VALID);
        if(response.status()==201) recordTargetArtifacts("OSC".equals(type)?"OSC":"POST",targetId);
        return response;
    }

    private Map<String,Object> reportBody(String type,Integer id,String reason,String details) {
        return Map.of("tipo",type,"alvoId",id,"motivo",reason,"justificativa",details);
    }

    private int reportCount(String type,Integer id,String status) {
        return jdbc.queryForObject("SELECT count(*) FROM public.content_reports WHERE target_type=? AND target_id=? AND status=?",Integer.class,type,id,status);
    }

    private int blockCount(String type,Integer id,String source,boolean active) {
        return jdbc.queryForObject("SELECT count(*) FROM public.content_moderation_blocks WHERE target_type=? AND target_id=? AND source=? AND active=?",Integer.class,type,id,source,active);
    }

    private Long activeBlockId(String type,Integer id,String source) {
        return jdbc.queryForObject("SELECT id FROM public.content_moderation_blocks WHERE target_type=? AND target_id=? AND source=? AND active",Long.class,type,id,source);
    }

    private int reviewHistoryCount(String type,Integer id) {
        return jdbc.queryForObject("SELECT count(*) FROM public.content_report_review_history WHERE target_type=? AND target_id=?",Integer.class,type,id);
    }

    private int reviewHistoryCountForDecision(String type,Integer id,String decision) {
        return jdbc.queryForObject("SELECT count(*) FROM public.content_report_review_history WHERE target_type=? AND target_id=? AND decision=?",Integer.class,type,id,decision);
    }

    private void insertSyntheticPendingReportAt(Integer targetId, int reporterOrdinal, int hoursAgo, int microsecondsAgo) {
        String reporterKey = String.format(java.util.Locale.ROOT, "%064x", reporterOrdinal);
        assertEquals(1, jdbc.update("""
                INSERT INTO public.content_reports(target_type,target_id,content_kind,reporter_key,reason,created_at)
                VALUES ('POST',?,'PUBLICATION',?,'OTHER',
                        CURRENT_TIMESTAMP - (? * INTERVAL '1 hour') - (? * INTERVAL '1 microsecond'))
                """, targetId, reporterKey, hoursAgo, microsecondsAgo));
        try { recordTargetArtifacts("POST", targetId); }
        catch (IOException failure) { throw new IllegalStateException("Unable to record synthetic report fixture.", failure); }
    }

    private void insertSyntheticBlock(String type,Integer id,String kind,String source) {
        jdbc.update("INSERT INTO public.content_moderation_blocks(target_type,target_id,content_kind,source,reason) "
                + "VALUES(?,?,?,?,'Synthetic fixture independent restriction') ON CONFLICT (target_type,target_id,source) WHERE active DO NOTHING",
                type,id,kind,source);
        try { recordTargetArtifacts(type,id); } catch(IOException e) { throw new IllegalStateException("Unable to record synthetic moderation IDs.",e); }
    }

    private void recordTargetArtifacts(String type,Integer id) throws IOException {
        jdbc.query("SELECT id FROM public.content_reports WHERE target_type=? AND target_id=?",rs->{long value=rs.getLong(1);if(reportIds.add(value))recordFixtureUnchecked("REPORT",value);},type,id);
        jdbc.query("SELECT id FROM public.content_moderation_blocks WHERE target_type=? AND target_id=?",rs->{long value=rs.getLong(1);if(blockIds.add(value))recordFixtureUnchecked("BLOCK",value);},type,id);
        jdbc.query("SELECT id FROM public.content_report_review_history WHERE target_type=? AND target_id=?",rs->{long value=rs.getLong(1);if(reviewIds.add(value))recordFixtureUnchecked("REPORT_REVIEW_HISTORY_RETAINED",value);},type,id);
    }

    private void recordStatusHistory(Integer id) throws IOException {
        jdbc.query("SELECT id FROM public.historico_status_osc WHERE id_osc_snapshot=?",(RowCallbackHandler)rs->recordFixtureUnchecked("OSC_STATUS_HISTORY_RETAINED",rs.getLong(1)),id);
    }

    private boolean containsId(JsonNode tree,int id) {
        if(tree==null) return false;
        if(tree.isObject()) { var it=tree.fields(); while(it.hasNext()){var e=it.next(); if(e.getKey().equals("id")&&e.getValue().asInt()==id)return true; if(containsId(e.getValue(),id))return true;} }
        if(tree.isArray()) for(JsonNode child:tree) if(containsId(child,id))return true;
        return false;
    }

    private UserFixture createUser(String label,boolean admin) throws IOException {
        String email="reportqa-"+UUID.randomUUID()+"@example.com";
        Role role=roles.findByNome("USUARIO").orElseThrow();
        Usuario user=new Usuario(); user.setCpf(new Cpf(validCpf())); user.setEmail(new Email(email));
        user.setNome("Synthetic content reporter "+label); user.setDataNascimento(LocalDate.of(1990,1,1));
        user.setGenero(Genero.PREFIRO_NAO_INFORMAR); user.setSenha(encoder.encode(PASSWORD));
        user.setEmailVerificado(true); user.setTelefoneVerificado(admin); user.setAtivo(true);
        if(admin) { user.setTelefone("+5511999999999"); user.getRoles().add(roles.findByNome("ADMIN").orElseThrow()); }
        user.setRoles(new HashSet<>(user.getRoles())); user.getRoles().add(role);
        user=users.saveAndFlush(user); userIds.add(user.getId()); recordFixture("USER",user.getId());
        assertNotEquals(47,user.getId()); assertNotEquals(48,user.getId());
        UserFixture fixture=new UserFixture(user,email); userFixtures.put(user.getId(),fixture); return fixture;
    }

    private Osc createOsc(Usuario owner,String label) throws IOException {
        Osc value=new Osc(); value.setCnpj(new Cnpj(validCnpj())); value.setUsuario(owner);
        value.setNome("Synthetic report NGO "+label+" "+UUID.randomUUID().toString().substring(0,8));
        value.setDescricao("Synthetic fixture for content-report testing.");
        value.setEmail(new Email("ngo-"+UUID.randomUUID()+"@example.com")); value.setCidade("Santos");
        value.setCep("11000000"); value.setBairro("Centro"); value.setRua("Rua sintética");
        value.setStatus(StatusOsc.ATIVA); value=oscs.saveAndFlush(value); oscIds.add(value.getId()); recordFixture("OSC",value.getId());
        createMembership(value,owner,CargoOsc.CRIADOR); return value;
    }

    private void createMembership(Osc organization,Usuario user,CargoOsc cargo) {
        OscMembros member=new OscMembros(); member.setOsc(organization); member.setUsuario(user);
        member.setCargoOsc(cargo); member.setDataCriacao(LocalDateTime.now()); member.inicializarId(); members.saveAndFlush(member);
    }

    private Post createPost(Osc organization,Usuario author,TipoPost type,boolean withVacancy) throws IOException {
        Post post=new Post(); post.setOsc(organization); post.setUsuario(author); post.setTipo(type); post.setAtivo(true);
        post.setTitulo("Synthetic report "+type+" "+UUID.randomUUID().toString().substring(0,8));
        post.setConteudo("Synthetic public content for reporting integration tests.");
        post=posts.saveAndFlush(post); postIds.add(post.getId()); postTypes.put(post.getId(),type); recordFixture("POST",post.getId());
        if(withVacancy) {
            Vaga v=new Vaga(); v.setPost(post); v.setQuantidadeVagas(2); v.setModalidade(ModalidadeVoluntariado.PRESENCIAL);
            v.setRequisitos("Synthetic requirements."); v.setCargaHoraria("4h semanais"); v.setCidade("Santos");
            v.setBairro("Centro"); v.setRua("Rua sintética"); v.setNumero("1"); v.setDataInicio(LocalDate.now());
            v.setDataFim(LocalDate.now().plusDays(30)); v.setStatus(StatusVaga.ABERTA); v=vacancies.saveAndFlush(v);
            vacancyIds.add(v.getId()); recordFixture("VAGA",v.getId()); post.setVagaVoluntariado(v);
        }
        return post;
    }

    private AdminFixture createAdminSession(AdminProfile profile,String label) throws IOException {
        UserFixture fixture=createUser(label,true); Usuario admin=fixture.user();
        admin.setTelefoneVerificado(true); admin=users.saveAndFlush(admin);
        Instant now=Instant.now(); UUID assignment=UUID.randomUUID();
        adminAssignments.insert(new AdminAssignment(assignment,admin.getId(),admin.getId(),profile,
                AdminAssignmentStatus.ACTIVE,now,admin.getId(),now,admin.getId(),null,"Synthetic report test assignment"));
        assignmentIds.add(assignment); recordFixture("ADMIN_ASSIGNMENT",assignment.toString());
        UUID challengeId=UUID.randomUUID(); AdminAuthChallenge challenge=new AdminAuthChallenge();
        challenge.setId(challengeId); challenge.setUsuario(admin); challenge.setAccountKeyHash(authCrypto.accountKey(fixture.email()));
        challenge.setIpKeyHash(authCrypto.ipKey(ip)); challenge.setEmailSnapshotHash(authCrypto.accountKey(fixture.email()));
        challenge.setPhoneSnapshotHash(authCrypto.phoneKey(admin.getTelefone())); challenge.setState(AdminAuthChallenge.State.VERIFIED);
        challenge.setCreatedAt(now); challenge.setExpiresAt(now.plusSeconds(600)); challenge.setEmailVerifiedAt(now);
        challenge.setSmsVerifiedAt(now); challenge.setCompletedAt(now); adminChallenges.saveAndFlush(challenge);
        challengeIds.add(challengeId); recordFixture("ADMIN_CHALLENGE",challengeId.toString());
        String raw=adminPrivilegedSessions.emitir(challengeId,ip).rawCredential();
        UUID session=jdbc.queryForObject("SELECT id FROM public.admin_privileged_sessions WHERE challenge_id=?",UUID.class,challengeId);
        adminSessionIds.add(session); recordFixture("ADMIN_SESSION",session.toString());
        UUID audit=jdbc.queryForObject("SELECT id FROM public.admin_security_audit_events WHERE challenge_id=? AND event_type='ADMIN_SESSION_ISSUED' ORDER BY occurred_at DESC LIMIT 1",UUID.class,challengeId);
        recordFixture("ADMIN_AUTH_AUDIT_RETAINED",audit.toString());
        return new AdminFixture(admin,AdminSessionCookie.NAME+"="+raw);
    }

    private String login(UserFixture user) throws Exception {
        HttpResult response=request("POST","/usuario/login",Map.of("email",user.email(),"senha",PASSWORD,"lembrarDeMim",true),null,Csrf.VALID);
        assertEquals(200,response.status(),"Synthetic account login failed.");
        String cookie=cookie(response,"PA_ACCESS"); accessCookies.put(user.user().getId(),cookie); return cookie;
    }

    private HttpResult request(String method,String path,Object body,String cookie,Csrf csrf) throws Exception {
        return send(prepare(method,path,body,cookie,csrf));
    }

    private PreparedRequest prepare(String method,String path,Object body,String cookie,Csrf csrfMode) throws Exception {
        var builder=java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl()+path))
                .header("Accept",MediaType.APPLICATION_JSON_VALUE).header("X-Real-IP",ip);
        List<String> cookies=new ArrayList<>();
        if(csrfMode!=Csrf.NONE) {
            var response=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl()+"/usuario/csrf")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            JsonNode csrf=mapper.readTree(response.body());
            cookies.add(response.headers().allValues("Set-Cookie").stream().map(v->v.split(";",2)[0]).filter(v->v.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow());
            builder.header(csrf.path("headerName").asText(),csrfMode==Csrf.VALID?csrf.path("token").asText():"synthetic-invalid-csrf");
        }
        if(cookie!=null) cookies.add(cookie);
        if(!cookies.isEmpty()) builder.header("Cookie",String.join("; ",cookies));
        if(body==null) builder.method(method,java.net.http.HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type",MediaType.APPLICATION_JSON_VALUE).method(method,java.net.http.HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return new PreparedRequest(builder.build());
    }

    private HttpResult send(PreparedRequest request) throws Exception {
        var response=http.send(request.request(),java.net.http.HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(),response.headers().allValues("Set-Cookie"),response.body());
    }

    private List<HttpResult> simultaneously(PreparedRequest first,PreparedRequest second) throws Exception {
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<HttpResult> a=pool.submit(()->{ready.countDown();assertTrue(start.await(15,TimeUnit.SECONDS));return send(first);});
            Future<HttpResult> b=pool.submit(()->{ready.countDown();assertTrue(start.await(15,TimeUnit.SECONDS));return send(second);});
            assertTrue(ready.await(15,TimeUnit.SECONDS)); start.countDown();
            return List.of(a.get(45,TimeUnit.SECONDS),b.get(45,TimeUnit.SECONDS));
        }
    }

    private String cookie(HttpResult response,String name) {
        return response.cookies().stream().map(v->v.split(";",2)[0]).filter(v->v.startsWith(name+"="))
                .map(v->v.substring(name.length()+1)).map(v->name+"="+v).findFirst().orElseThrow();
    }

    private JsonNode json(HttpResult response) throws IOException { return mapper.readTree(response.body()); }
    private String baseUrl() { return "http://127.0.0.1:"+port; }
    private void recordFixture(String kind,Object id) throws IOException {
        Path path=Path.of(ledger); Files.createDirectories(path.getParent());
        Files.writeString(path,kind+":"+id+System.lineSeparator(),StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
    }

    private void recordFixtureUnchecked(String kind,Object id) {
        try { recordFixture(kind,id); } catch(IOException e) { throw new IllegalStateException("Unable to write the synthetic fixture ledger.",e); }
    }

    private void assertNextSyntheticUserIdCannotBe47Or48() {
        String sequence=jdbc.queryForObject("SELECT pg_get_serial_sequence('public.usuarios','id')",String.class);
        assertNotNull(sequence,"User ID generation sequence must be discoverable before fixture creation.");
        String safeSequence=sequence.replace("\"","");
        assertTrue(safeSequence.matches("public\\.[A-Za-z0-9_]+"),"Unexpected users sequence name; refuse fixture creation.");
        Map<String,Object> state=jdbc.queryForMap("SELECT last_value,is_called FROM "+safeSequence);
        long last=((Number)state.get("last_value")).longValue();
        long increment=jdbc.queryForObject(
                "SELECT seqincrement FROM pg_sequence WHERE seqrelid=?::regclass",Long.class,safeSequence);
        boolean called=(Boolean)state.get("is_called");
        assertTrue(increment > 0,"Refuse fixture creation when the user ID sequence is not increasing.");
        long next=called?last+increment:last;
        assertTrue(next>48,"Refuse to create synthetic users until the users identity sequence is above protected IDs 47/48.");
    }

    private String validCpf() {
        SecureRandom random=new SecureRandom();
        while(true) {
            int[] d=new int[11]; for(int i=0;i<9;i++) d[i]=random.nextInt(10);
            for(int p=9;p<11;p++){int sum=0;for(int i=0;i<p;i++)sum+=d[i]*(p+1-i);int r=(sum*10)%11;d[p]=r==10?0:r;}
            String value=java.util.Arrays.stream(d).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining());
            try{return new Cpf(value).valor();}catch(IllegalArgumentException ignored){}
        }
    }

    private String validCnpj() {
        SecureRandom random=new SecureRandom();
        while(true){int[] d=new int[14];for(int i=0;i<12;i++)d[i]=random.nextInt(10);int[] a={5,4,3,2,9,8,7,6,5,4,3,2};int sum=0;for(int i=0;i<12;i++)sum+=d[i]*a[i];d[12]=sum%11<2?0:11-sum%11;int[] b={6,5,4,3,2,9,8,7,6,5,4,3,2};sum=0;for(int i=0;i<13;i++)sum+=d[i]*b[i];d[13]=sum%11<2?0:11-sum%11;String value=java.util.Arrays.stream(d).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining());try{return new Cnpj(value).getCnpj();}catch(IllegalArgumentException ignored){}}
    }

    private void cleanupCredentialsAndUsers() {
        for(UUID session:adminSessionIds) jdbc.update("DELETE FROM public.admin_privileged_sessions WHERE id=?",session);
        for(UUID challenge:challengeIds) jdbc.update("DELETE FROM public.admin_auth_challenges WHERE id=?",challenge);
        for(UUID assignment:assignmentIds) jdbc.update("DELETE FROM public.admin_assignments WHERE id=?",assignment);
        for(Integer userId:userIds) {
            jdbc.update("DELETE FROM public.sessoes_usuario WHERE usuario_id=?",userId);
            jdbc.update("DELETE FROM public.usuario_roles WHERE id_usuario=?",userId);
            String account=authCrypto.hashIdentifier("control:START_ACCOUNT","content-report-user:"+userId);
            jdbc.update("DELETE FROM public.admin_auth_controls WHERE control_key=?",account);
            UserFixture fixture=userFixtures.get(userId);
            if(fixture!=null) {
                String loginFailure=authCrypto.hashIdentifier("control:ACCOUNT_FAILURE","user-login-failure:"+fixture.email().trim().toLowerCase(java.util.Locale.ROOT));
                jdbc.update("DELETE FROM public.admin_auth_controls WHERE control_key=?",loginFailure);
            }
        }
        String reportIp=authCrypto.hashIdentifier("control:START_IP","content-report-ip:"+ip);
        String loginIp=authCrypto.hashIdentifier("control:START_IP","user-login-ip:"+ip);
        jdbc.update("DELETE FROM public.admin_auth_controls WHERE control_key IN (?,?)",reportIp,loginIp);
        for(Integer userId:userIds) {
            if (retainedUsers.contains(userId)) {
                assertEquals(1, jdbc.update("""
                        UPDATE public.usuarios
                           SET ativo=false,email_verificado=false,telefone_verificado=false,senha_hash=?
                         WHERE id=?
                        """, encoder.encode(UUID.randomUUID().toString()), userId));
                recordFixtureUnchecked("RETAINED_AUTHOR_USER_DEACTIVATED", userId);
                continue;
            }
            jdbc.update("UPDATE public.post SET id_usuario=NULL WHERE id_usuario=? AND id IN (SELECT id FROM public.post WHERE id_osc IN (?))",userId,osc.getId());
            jdbc.update("DELETE FROM public.usuarios WHERE id=?",userId);
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.usuarios WHERE id=?",Integer.class,userId));
        }
    }

    private void retainReviewedTargetsAndHide() throws IOException {
        for(Integer postId:postIds) {
            int history=jdbc.queryForObject("SELECT count(*) FROM public.content_report_review_history WHERE target_type='POST' AND target_id=?",Integer.class,postId);
            if(history>0) {
                retainedPosts.add(postId);
                Integer parent=jdbc.queryForObject("SELECT id_osc FROM public.post WHERE id=?",Integer.class,postId);
                if(parent!=null)retainedOscs.add(parent);
                Integer author=jdbc.queryForObject("SELECT id_usuario FROM public.post WHERE id=?",Integer.class,postId);
                if(author!=null) {
                    retainedUsers.add(author);
                    recordFixture("RETAINED_POST_AUTHOR_USER",author);
                }
                jdbc.update("UPDATE public.post SET ativo=false WHERE id=?",postId);
                recordFixture("RETAINED_POST_FOR_APPEND_ONLY_HISTORY",postId);
            }
        }
        for(Integer oscId:oscIds) {
            int review=jdbc.queryForObject("SELECT count(*) FROM public.content_report_review_history WHERE target_type='OSC' AND target_id=?",Integer.class,oscId);
            int statusHistory=jdbc.queryForObject("SELECT count(*) FROM public.historico_status_osc WHERE id_osc_snapshot=?",Integer.class,oscId);
            if(review>0||statusHistory>0) retainedOscs.add(oscId);
        }
        for(Integer postId:retainedPosts) {
            TipoPost type=postTypes.get(postId);
            insertRetentionBlock("POST",postId,type==TipoPost.VOLUNTARIADO?"VOLUNTEERING":"PUBLICATION");
            recordFixture("RETAINED_POST_FOR_APPEND_ONLY_HISTORY",postId);
        }
        for(Integer oscId:retainedOscs) {
            int review=jdbc.queryForObject("SELECT count(*) FROM public.content_report_review_history WHERE target_type='OSC' AND target_id=?",Integer.class,oscId);
            if(review==0) deleteTargetData("OSC",oscId);
            deactivateRetainedSyntheticOrganization(oscId);
            insertRetentionBlock("OSC",oscId,"OSC");
            jdbc.update("UPDATE public.oscs SET id_usuario_criador=NULL WHERE id=?",oscId);
            jdbc.update("DELETE FROM public.osc_membros WHERE id_osc=?",oscId);
            jdbc.update("DELETE FROM public.osc_causas WHERE id_osc=?",oscId);
            jdbc.update("DELETE FROM public.osc_ods WHERE id_osc=?",oscId);
            recordFixture("RETAINED_OSC_FOR_APPEND_ONLY_HISTORY",oscId);
        }
    }

    private void deactivateRetainedSyntheticOrganization(Integer oscId) throws IOException {
        Long historyId=new TransactionTemplate(transactionManager).execute(status -> {
            int changed=jdbc.update("""
                    UPDATE public.oscs
                       SET status='INATIVA',origem_ultima_desativacao='INDETERMINADA',
                           data_ultima_desativacao=CURRENT_TIMESTAMP,
                           motivo_ultima_desativacao='Synthetic fixture hidden after preserving immutable reporting history.'
                     WHERE id=? AND status='ATIVA'
                    """,oscId);
            if(changed==0) return null;
            jdbc.update("""
                    INSERT INTO public.historico_status_osc
                        (id_osc,id_osc_snapshot,status_anterior,status_novo,origem_acao,tipo_evento,
                         origem_desativacao_anterior,data_desativacao_anterior,id_usuario,
                         autoridade_ator,data_criacao,motivo,id_usuario_snapshot)
                    VALUES(?,?,'ATIVA','INATIVA','INDETERMINADA','TRANSICAO_STATUS',NULL,NULL,NULL,
                           'TEST_FIXTURE_CLEANUP',CURRENT_TIMESTAMP,
                           'Synthetic fixture hidden after preserving immutable reporting history.',NULL)
                    """,oscId,oscId);
            return jdbc.queryForObject("""
                    SELECT id FROM public.historico_status_osc
                     WHERE id_osc_snapshot=? AND autoridade_ator='TEST_FIXTURE_CLEANUP'
                     ORDER BY id DESC LIMIT 1
                    """,Long.class,oscId);
        });
        if(historyId!=null) recordFixture("OSC_STATUS_HISTORY_RETAINED",historyId);
    }

    private void deleteTargetData(String type,Integer id) throws IOException {
        jdbc.query("SELECT id FROM public.content_reports WHERE target_type=? AND target_id=?",rs->{long value=rs.getLong(1);if(reportIds.add(value))recordFixtureUnchecked("REPORT_CLEANED",value);},type,id);
        jdbc.query("SELECT id FROM public.content_moderation_blocks WHERE target_type=? AND target_id=?",rs->{long value=rs.getLong(1);if(blockIds.add(value))recordFixtureUnchecked("BLOCK_CLEANED",value);},type,id);
        jdbc.update("DELETE FROM public.content_reports WHERE target_type=? AND target_id=?",type,id);
        jdbc.update("DELETE FROM public.content_moderation_blocks WHERE target_type=? AND target_id=?",type,id);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.content_reports WHERE target_type=? AND target_id=?",Integer.class,type,id));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.content_moderation_blocks WHERE target_type=? AND target_id=?",Integer.class,type,id));
    }

    private void insertRetentionBlock(String type,Integer id,String kind) throws IOException {
        jdbc.update("INSERT INTO public.content_moderation_blocks(target_type,target_id,content_kind,source,reason) VALUES(?,?,?,'ADMIN_RESTRICTION','Synthetic integration target retained for immutable audit history') ON CONFLICT (target_type,target_id,source) WHERE active DO NOTHING",type,id,kind);
        jdbc.query("SELECT id FROM public.content_moderation_blocks WHERE target_type=? AND target_id=? AND source='ADMIN_RESTRICTION' AND active",rs->{long value=rs.getLong(1);if(blockIds.add(value))recordFixtureUnchecked("BLOCK_RETAINED",value);},type,id);
    }

    private record UserFixture(Usuario user,String email) { }
    private record AdminFixture(Usuario user,String cookie) { }
    private record PreparedRequest(java.net.http.HttpRequest request) { }
    private record HttpResult(int status,List<String> cookies,String body) { }
    private enum Csrf { NONE, INVALID, VALID }
}
