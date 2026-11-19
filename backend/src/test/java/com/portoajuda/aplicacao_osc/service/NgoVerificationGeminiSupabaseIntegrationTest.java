package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.StripeClient;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** One authorized TEST workflow: full app, real security chain, private TEST Storage and one targeted Gemini call. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.ngo-verification.enabled=true",
        "app.ngo-verification.ai-analysis.enabled=true",
        "app.ngo-verification.ai-analysis.results-read-enabled=true",
        "app.ngo-verification.ai-analysis.timeout=60s",
        "app.ngo-verification.ai-analysis.worker-enabled=true",
        "app.ngo-verification.ai-analysis.evaluation-worker-enabled=false",
        "app.ngo-verification.ai-analysis.poll-ms=250",
        "app.ngo-verification.ai-analysis.evaluation-max-provider-requests=3",
        "app.ngo-verification.ai-analysis.provider=gemini",
        "app.ngo-verification.ai-analysis.model=gemini-3.1-flash-lite",
        "app.ngo-verification.auto-approval.enabled=false",
        "app.initialization.seed-roles=false",
        "app.maintenance.cleanup-cron=-",
        "app.admin-auth.controls-cleanup-cron=-",
        "app.admin-auth.delivery-worker.enabled=false",
        "admin-auth.delivery-poll-ms=3600000",
        "app.cookies.secure=false",
        "ALLOWED_ORIGINS=http://localhost:8080",
        "APP_FRONTEND_URL=http://localhost:8080",
        "APP_BACKEND_URL=http://localhost:8080"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(NgoVerificationSupabaseTargetCondition.class)
class NgoVerificationGeminiSupabaseIntegrationTest {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger MOCK_IP_SEQUENCE = new AtomicInteger(1);
    private static final String ORGANIZATION_NAME = "ASSOCIACAO FICTICIA CEDAR TEST";
    private static final AtomicInteger CNPJA_MOCK_CALLS = new AtomicInteger();
    private static final AtomicInteger GEOCODING_MOCK_CALLS = new AtomicInteger();
    private static final HttpServer EXTERNAL_MOCK = startExternalMock();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired VerificacaoAnaliseOrchestrator orchestrator;
    @Autowired(required = false) VerificacaoAnaliseEvaluationWorker evaluationWorker;
    @Autowired AnaliseRepresentacaoDocumental geminiAdapter;
    @Autowired SessaoUsuarioService userSessions;
    @Autowired org.springframework.context.ApplicationContext applicationContext;
    @MockitoBean EmailService emailService;
    @MockitoBean SmsService smsService;
    @MockitoBean StripeClient stripeClient;

    private Integer userId;
    private String email;
    private long applicationId;
    private int oscId;
    private long submissionId;
    private long evidenceId;
    private long jobId;
    private final List<Long> eventIds = new ArrayList<>();
    private final List<Integer> syntheticUserIds = new ArrayList<>();
    private final List<Map<String, Object>> identityEvaluationCases = new ArrayList<>();
    private final Map<String, Object> ledger = new LinkedHashMap<>();
    private Path ledgerPath;
    private String cnpj;

    @DynamicPropertySource
    static void externalServicesAreMocked(DynamicPropertyRegistry properties) {
        String base = "http://127.0.0.1:" + EXTERNAL_MOCK.getAddress().getPort();
        properties.add("cnpja.url-base", () -> base + "/office/");
        properties.add("app.geocoding.base-url", () -> base);
    }

    @org.junit.jupiter.api.AfterAll
    static void stopLocalExternalServiceMocks() { EXTERNAL_MOCK.stop(0); }

    @Test
    void normalAuthenticatedSubmissionCreatesAndProcessesOnlyItsGeminiJob() throws Exception {
        Assumptions.assumeFalse(System.getProperty("ngo.ai.live-job-id") != null,
                "When an exact live job ID is supplied, do not create or process another evaluation fixture.");
        Assumptions.assumeFalse(System.getProperty("ngo.ai.identity-eval") != null,
                "The bounded identity evaluation uses its own dedicated method and fixtures.");
        Assumptions.assumeFalse(Boolean.getBoolean("ngo.ai.ledger-recovery"));
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("NGO_VERIFICATION_SUPABASE_TEST_CONFIRMED")));
        assertTrue(applicationContext.containsBean("verificacaoAnaliseWorker"),
                "The configured general worker processes new submissions after the queue preview found no backlog.");
        assertFalse(applicationContext.containsBean("verificacaoAnaliseEvaluationWorker"),
                "Only the general queue worker is enabled for this isolated synthetic workflow.");
        assertTrue(applicationContext.containsBean("ngoVerificationAiExecutor"),
                "Only the exact-ID evaluation worker may own a bounded executor.");

        String runId = UUID.randomUUID().toString();
        ledger.put("runId", runId);
        ledger.put("databaseTarget", "authorized Supabase TEST; IDs only");
        ledger.put("migration", "20261114 applied before test; not rerun by this class");
        ledgerPath = ledgerPath(runId);
        writeLedger();

        String password = "Synthetic-QA-" + UUID.randomUUID() + "-A9!";
        email = "ngo-ai-synthetic-" + runId + "@example.invalid";
        userId = createSyntheticUser(email, password);
        ledger.put("applicantUserId", userId);
        CookieSession session = login(email, password);
        cnpj = syntheticCnpj();
        oscId = registerSyntheticOrganization(session, cnpj);
        ledger.put("oscId", oscId);
        writeLedger();
        assertEquals(1, CNPJA_MOCK_CALLS.get());
        assertEquals(1, GEOCODING_MOCK_CALLS.get());

        JsonNode applicantView = json(mockMvc.perform(get("/osc/" + oscId + "/verificacao")
                .cookie(session.cookies())).andReturn(), 200);
        applicationId = applicantView.path("applicationId").asLong();
        assertTrue(applicationId > 0);
        assertEquals("PENDING", applicantView.path("status").asText());
        ledger.put("applicationId", applicationId);
        writeLedger();

        MvcResult upload = mockMvc.perform(multipart("/osc/" + oscId + "/verificacao/documentos")
                .file(new MockMultipartFile("arquivo", "evidencia-ficticia.pdf", "application/pdf", syntheticPdf()))
                .param("tipo", "REPRESENTATION_PROOF")
                .cookie(session.cookies())
                .header(session.csrfHeader(), session.csrfToken())).andReturn();
        assertEquals(201, upload.getResponse().getStatus(), "The normal authenticated upload flow should store only fictitious evidence in the private TEST bucket.");
        JsonNode uploaded = objectMapper.readTree(upload.getResponse().getContentAsByteArray());
        evidenceId = uploaded.path("evidence").get(0).path("id").asLong();
        assertTrue(evidenceId > 0);
        ledger.put("evidenceId", evidenceId);
        writeLedger();

        JsonNode submitted = json(mockMvc.perform(post("/osc/" + oscId + "/verificacao/enviar")
                .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())).andReturn(), 200);
        assertEquals("PENDING", submitted.path("status").asText());
        applicationId = submitted.path("applicationId").asLong();
        submissionId = jdbc.queryForObject("""
                SELECT id FROM public.ngo_representative_verification_submissions
                 WHERE application_id=? AND version_number=1 AND submitted_at IS NOT NULL
                """, Long.class, applicationId);
        assertTrue(submissionId > 0);
        ledger.put("submissionId", submissionId);
        ledger.put("applicationStatusAfterSubmit", "PENDING");
        writeLedger();

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                Integer.class, applicationId), "One submitted version must enqueue one idempotent job.");
        jobId = jdbc.queryForObject("SELECT id FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                Long.class, applicationId);
        ledger.put("aiJobId", jobId);
        writeLedger();

        // The duplicate is rejected by the normal application state machine before another enqueue/request.
        MvcResult duplicate = mockMvc.perform(post("/osc/" + oscId + "/verificacao/enviar")
                .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())).andReturn();
        assertEquals(409, duplicate.getResponse().getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                Integer.class, applicationId));
        awaitSyntheticJobCompletion(jobId, java.time.Duration.ofSeconds(90));

        Map<String, Object> saved = jdbc.queryForMap("""
                SELECT status,last_error_code,provider,model,input_tokens,output_tokens,
                       result_summary::text AS result_summary,completed_at,attempts
                  FROM public.ngo_representative_verification_ai_jobs WHERE id=?
                """, jobId);
        assertEquals("COMPLETED", saved.get("status"));
        assertEquals("gemini", saved.get("provider"));
        assertEquals("gemini-3.1-flash-lite", saved.get("model"));
        assertEquals(1, saved.get("attempts"));
        assertNull(saved.get("last_error_code"));
        assertNotNull(saved.get("input_tokens"));
        assertNotNull(saved.get("output_tokens"));
        assertNotNull(saved.get("completed_at"));
        JsonNode result = objectMapper.readTree((String) saved.get("result_summary"));
        assertTrue(List.of("INCONCLUSIVO", "DIVERGENCIA").contains(result.path("conclusion").asText()),
                "Unclear or discrepant synthetic evidence remains a human-review finding.");
        assertTrue(result.has("fields"));
        assertTrue(result.has("discrepancies"));
        assertFalse(result.toString().contains("FICTITIOUS SIGNATURE"));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM public.ngo_representative_verification_applications WHERE id=?", String.class, applicationId),
                "AI findings must never decide or approve the application.");

        eventIds.addAll(jdbc.query("SELECT id FROM public.ngo_representative_verification_ai_job_events WHERE job_id=? ORDER BY id",
                (rs, row) -> rs.getLong(1), jobId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_job_events WHERE job_id=? AND event_type='CLAIMED'",
                Integer.class, jobId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_job_events WHERE job_id=? AND event_type='COMPLETED'",
                Integer.class, jobId));
        ledger.put("aiJobEventIds", eventIds);
        ledger.put("aiResultStatus", saved.get("status"));
        ledger.put("inputTokens", saved.get("input_tokens"));
        ledger.put("outputTokens", saved.get("output_tokens"));
        ledger.put("conclusion", result.path("conclusion").asText());
        ledger.put("applicationStatusAfterAI", "PENDING");
        writeLedger();

        // Replayed submission cannot re-enqueue or generate a second provider request.
        MvcResult afterCompletionDuplicate = mockMvc.perform(post("/osc/" + oscId + "/verificacao/enviar")
                .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())).andReturn();
        assertEquals(409, afterCompletionDuplicate.getResponse().getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                Integer.class, applicationId));
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                Integer.class, jobId));

        System.out.println("NGO_GEMINI_TEST_LEDGER=" + ledgerPath);
        Map<String, Object> testReport = Map.ofEntries(
                Map.entry("applicationId", applicationId), Map.entry("oscId", oscId), Map.entry("userId", userId),
                Map.entry("submissionId", submissionId), Map.entry("evidenceId", evidenceId),
                Map.entry("jobId", jobId), Map.entry("jobEvents", eventIds), Map.entry("status", saved.get("status")),
                Map.entry("inputTokens", saved.get("input_tokens")), Map.entry("outputTokens", saved.get("output_tokens")),
                Map.entry("result", result));
        System.out.println("NGO_GEMINI_TEST_RESULT=" + objectMapper.writeValueAsString(testReport));
    }

    private void awaitSyntheticJobCompletion(long exactJobId, java.time.Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Map<String, Object> status = jdbc.queryForMap(
                    "SELECT status,last_error_code,attempts FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                    exactJobId);
            String state = (String) status.get("status");
            if ("COMPLETED".equals(state)) return;
            if ("FAILED".equals(state) || "SUPERSEDED".equals(state)) {
                fail("Synthetic analysis job ended in " + state + " with category " + status.get("last_error_code"));
            }
            Thread.sleep(250);
        }
        fail("Synthetic analysis job did not complete before the bounded test deadline.");
    }

    @Test
    void newlySubmittedEvaluationJobIsAutomaticallyClaimedByTheScopedWorker() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("ngo.ai.automatic-worker-eval"),
                "One real request is allowed only with the explicit automatic-worker evaluation switch.");
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("NGO_VERIFICATION_SUPABASE_TEST_CONFIRMED")));
        Assumptions.assumeFalse(Boolean.getBoolean("ngo.ai.ledger-recovery"));
        assertFalse(applicationContext.containsBean("verificacaoAnaliseWorker"),
                "The unrestricted worker must remain disabled.");
        assertTrue(applicationContext.containsBean("verificacaoAnaliseEvaluationWorker"));
        Map<String, Object> historical = jdbc.queryForMap("""
                SELECT j.id,j.application_id,s.version_number,j.attempts
                  FROM public.ngo_representative_verification_ai_jobs j
                  JOIN public.ngo_representative_verification_submissions s ON s.id=j.submission_id
                 WHERE j.status='COMPLETED' ORDER BY j.created_at DESC,j.id DESC LIMIT 1
                """);
        long historicalJobId = ((Number) historical.get("id")).longValue();
        long historicalApplicationId = ((Number) historical.get("application_id")).longValue();
        int historicalVersion = ((Number) historical.get("version_number")).intValue();
        int historicalAttempts = ((Number) historical.get("attempts")).intValue();
        assertEquals("COMPLETED", orchestrator.latestForAdmin(historicalApplicationId, historicalVersion)
                .orElseThrow().status(), "Stored completed results remain readable without reprocessing.");
        assertEquals(0, evaluationWorker.providerCallsUsed(), "Reading history must not call the provider.");

        String runId = UUID.randomUUID().toString();
        ledger.put("runId", runId);
        ledger.put("purpose", "one synthetic submission processed automatically by exact-ID evaluation worker");
        ledger.put("databaseTarget", "authorized Supabase TEST; synthetic IDs only");
        ledger.put("migration", "20261114 already applied; no migration executed");
        ledgerPath = ledgerPath(runId);
        writeLedger();

        String password = "Synthetic-Auto-" + UUID.randomUUID() + "-Q8!";
        email = "ngo-ai-auto-synthetic-" + runId + "@example.invalid";
        userId = createSyntheticUser(email, password);
        ledger.put("applicantUserId", userId);
        writeLedger();
        CookieSession session = login(email, password);
        cnpj = syntheticCnpj();
        oscId = registerSyntheticOrganization(session, cnpj);
        ledger.put("oscId", oscId);
        JsonNode application = json(mockMvc.perform(get("/osc/" + oscId + "/verificacao")
                .cookie(session.cookies())).andReturn(), 200);
        applicationId = application.path("applicationId").asLong();
        assertTrue(applicationId > 0);
        assertEquals("PENDING", application.path("status").asText());
        ledger.put("applicationId", applicationId);
        writeLedger();

        MvcResult uploaded = mockMvc.perform(multipart("/osc/" + oscId + "/verificacao/documentos")
                .file(new MockMultipartFile("arquivo", "synthetic-auto-evaluation.pdf", "application/pdf", syntheticPdf()))
                .param("tipo", "REPRESENTATION_PROOF")
                .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())).andReturn();
        assertEquals(201, uploaded.getResponse().getStatus());
        evidenceId = objectMapper.readTree(uploaded.getResponse().getContentAsByteArray())
                .path("evidence").get(0).path("id").asLong();
        ledger.put("evidenceId", evidenceId);
        JsonNode submitted = json(mockMvc.perform(post("/osc/" + oscId + "/verificacao/enviar")
                .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())).andReturn(), 200);
        assertEquals("PENDING", submitted.path("status").asText());
        submissionId = jdbc.queryForObject("""
                SELECT id FROM public.ngo_representative_verification_submissions
                 WHERE application_id=? AND version_number=1 AND submitted_at IS NOT NULL
                """, Long.class, applicationId);
        jobId = jdbc.queryForObject("""
                SELECT id FROM public.ngo_representative_verification_ai_jobs
                 WHERE application_id=? AND submission_id=?
                """, Long.class, applicationId, submissionId);
        ledger.put("submissionId", submissionId);
        ledger.put("aiJobId", jobId);
        writeLedger();

        assertTrue(jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                Integer.class, applicationId) == 1);
        // The post-commit enqueue event scopes only this newly-created job; the test never claims it manually.
        assertTrue(awaitJobStatus(jobId, "COMPLETED", java.time.Duration.ofSeconds(90)),
                "The scheduled scoped worker must claim, process, and persist the submitted job.");
        assertFalse(evaluationWorker.hasRegisteredJob(jobId));
        assertEquals(1, evaluationWorker.providerCallsUsed());

        Map<String, Object> saved = jdbc.queryForMap("""
                SELECT status,attempts,provider,model,input_tokens,output_tokens,result_summary::text AS result_summary
                  FROM public.ngo_representative_verification_ai_jobs WHERE id=?
                """, jobId);
        assertEquals("COMPLETED", saved.get("status"));
        assertEquals(1, saved.get("attempts"));
        assertEquals("gemini", saved.get("provider"));
        assertEquals("gemini-3.1-flash-lite", saved.get("model"));
        assertNotNull(saved.get("input_tokens"));
        assertNotNull(saved.get("output_tokens"));
        JsonNode result = objectMapper.readTree((String) saved.get("result_summary"));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM public.ngo_representative_verification_applications WHERE id=?",
                String.class, applicationId), "Advisory analysis must leave the decision pending for a human.");

        assertEquals(409, mockMvc.perform(post("/osc/" + oscId + "/verificacao/enviar")
                .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())).andReturn()
                .getResponse().getStatus());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/osc/" + oscId + "/verificacao").cookie(session.cookies())).andReturn();
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                Integer.class, applicationId));
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                Integer.class, jobId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_job_events WHERE job_id=? AND event_type='COMPLETED'",
                Integer.class, jobId));
        assertEquals(historicalAttempts, jdbc.queryForObject(
                "SELECT attempts FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                Integer.class, historicalJobId), "The scoped worker must not claim historical jobs.");

        // Semantic evaluation runs after the lifecycle/idempotence assertions so a model finding cannot mask them.
        Map<String, String> fieldStates = new HashMap<>();
        result.path("fields").forEach(field ->
                fieldStates.put(field.path("field").asText(), field.path("status").asText()));
        assertEquals("ENCONTRADO", fieldStates.get("NOME_ENTIDADE"));
        assertEquals("ENCONTRADO", fieldStates.get("CNPJ"));
        assertEquals("AUSENTE", fieldStates.get("REPRESENTANTE_LEGAL"));
        assertEquals("INCERTO", fieldStates.get("PODERES_REPRESENTACAO"),
                "The document does not establish authority; an uncertain extraction remains for human review.");
        assertEquals("DIVERGENCIA", result.path("conclusion").asText());
        assertEquals("INSUFFICIENT_EVIDENCE", result.path("identityComparison").asText());
        assertTrue(result.path("discrepancies").toString().contains("REPRESENTANTE_LEGAL_NAO_IDENTIFICADO"));
        assertNotEquals("EVIDENCIA_COMPATIVEL", result.path("conclusion").asText(),
                "An unsigned fictitious document with no named representative cannot be treated as positive proof.");

        ledger.put("resultStatus", saved.get("status"));
        ledger.put("providerCalls", evaluationWorker.providerCallsUsed());
        ledger.put("applicationStatusAfterAnalysis", "PENDING");
        writeLedger();
        System.out.println("NGO_GEMINI_AUTOMATIC_WORKER_LEDGER=" + ledgerPath);
        System.out.println("NGO_GEMINI_AUTOMATIC_WORKER_RESULT=" + objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("applicationId", applicationId), Map.entry("oscId", oscId), Map.entry("userId", userId),
                Map.entry("submissionId", submissionId), Map.entry("evidenceId", evidenceId),
                Map.entry("jobId", jobId), Map.entry("status", saved.get("status")),
                Map.entry("attempts", saved.get("attempts")), Map.entry("inputTokens", saved.get("input_tokens")),
                Map.entry("outputTokens", saved.get("output_tokens")), Map.entry("applicationStatus", "PENDING"))));
    }

    private boolean awaitJobStatus(long exactJobId, String expected, java.time.Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            String status = jdbc.queryForObject("SELECT status FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                    String.class, exactJobId);
            if (expected.equals(status)) return true;
            if ("FAILED".equals(status) || "SUPERSEDED".equals(status)) return false;
            Thread.sleep(100);
        }
        return false;
    }

    @Test
    void evaluatesOnlyTwoNewIdentityCasesThroughNormalSubmissionAndExactJobs() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("ngo.ai.identity-eval"),
                "The two-request identity evaluation requires an explicit test-process opt-in.");
        Assumptions.assumeFalse(Boolean.getBoolean("ngo.ai.ledger-recovery"));
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("NGO_VERIFICATION_SUPABASE_TEST_CONFIRMED")));
        assertTrue(applicationContext.containsBean("ngoVerificationAiExecutor"),
                "Only the exact-ID scoped worker may use the bounded provider executor.");
        assertFalse(applicationContext.containsBean("verificacaoAnaliseWorker"),
                "The global queue scan must remain disabled during bounded live evaluation.");

        String runId = UUID.randomUUID().toString();
        ledger.put("runId", runId);
        ledger.put("databaseTarget", "authorized Supabase TEST; synthetic IDs only");
        ledger.put("migration", "20261114 already applied; no migration executed");
        ledger.put("purpose", "two bounded live identity-comparison evaluations; maximum four provider requests");
        ledgerPath = ledgerPath(runId);
        writeLedger();

        List<IdentityEvaluationCase> standardCases = List.of(
                new IdentityEvaluationCase("civil_social_match", "LEGAL_REPRESENTATIVE",
                        "Amara Beatriz Synthetic Costa", "Amara Costa", "Amara Costa",
                        List.of(new EvidenceSpec("REPRESENTATION_PROOF", "synthetic-legal-proof.pdf",
                                List.of("SYNTHETIC EVALUATION DOCUMENT - NO REAL PERSON",
                                        "Organization: " + ORGANIZATION_NAME,
                                        "CNPJ: %CNPJ%", "Legal representative: Amara Beatriz Synthetic Costa",
                                        "Social name used in public representation: Amara Costa",
                                        "Mandate expires 2027-12-31"))),
                        "MATCHES_REGISTERED_DATA", "NOT_APPLICABLE", "APPARENTLY_CURRENT"),
                new IdentityEvaluationCase("delegate_missing_authority", "AUTHORIZED_DELEGATE",
                        "Caio Synthetic Lima", null, "Caio Synthetic Lima",
                        List.of(new EvidenceSpec("REPRESENTATION_PROOF", "synthetic-organization-proof.pdf",
                                        List.of("SYNTHETIC EVALUATION DOCUMENT - NO REAL PERSON",
                                                "Organization: " + ORGANIZATION_NAME,
                                                "CNPJ: %CNPJ%", "Legal representative: Bruna Synthetic Almeida")),
                                new EvidenceSpec("DELEGATION_AUTHORIZATION", "synthetic-incomplete-delegation.pdf",
                                        List.of("SYNTHETIC EVALUATION DOCUMENT - NO REAL PERSON",
                                                "Organization: " + ORGANIZATION_NAME,
                                                "CNPJ: %CNPJ%", "Proposed delegate: Caio Synthetic Lima",
                                                "This sample intentionally contains no authorizing representative, scope, or validity date."))),
                        "MATCHES_REGISTERED_DATA", "INCOMPLETE", "UNVERIFIABLE"));
        IdentityEvaluationCase completeCase = new IdentityEvaluationCase("complete_legal_consistent_authority",
                "LEGAL_REPRESENTATIVE", "Iara Beatriz Ficticia Costa", "Iara Costa", "Iara Costa",
                List.of(new EvidenceSpec("REPRESENTATION_PROOF", "synthetic-complete-legal-proof.pdf",
                        List.of("SYNTHETIC EVALUATION DOCUMENT - NO REAL PERSON",
                                "Organization: " + ORGANIZATION_NAME,
                                "CNPJ: %CNPJ%", "Legal representative: Iara Beatriz Ficticia Costa",
                                "Social name used in representation: Iara Costa",
                                "Authority: appointed by the fictitious organization to represent it before public authorities and sign operational submissions",
                                "The authority is documentary text only; no authenticity is asserted.",
                                "Mandate expiry date: 2027-12-31"))),
                "MATCHES_REGISTERED_DATA", "NOT_APPLICABLE", "APPARENTLY_CURRENT");
        List<IdentityEvaluationCase> cases = Boolean.getBoolean("ngo.ai.identity-complete-case")
                ? List.of(completeCase) : standardCases;

        int providerRequests = 0;
        for (IdentityEvaluationCase evaluation : cases) {
            Map<String, Object> caseLedger = new LinkedHashMap<>();
            identityEvaluationCases.add(caseLedger);
            caseLedger.put("case", evaluation.name());
            caseLedger.put("expectedIdentity", evaluation.expectedIdentity());
            caseLedger.put("expectedDelegationChain", evaluation.expectedDelegationChain());
            caseLedger.put("expectedValidity", evaluation.expectedValidity());
            String password = "Synthetic-Identity-QA-" + UUID.randomUUID() + "-B7!";
            String syntheticEmail = "ngo-id-" + runId.substring(0, 12) + "-"
                    + identityEvaluationCases.size() + "@example.invalid";
            userId = createSyntheticUser(syntheticEmail, password, evaluation.legalName(), evaluation.socialName());
            caseLedger.put("applicantUserId", userId);
            writeEvaluationCase(caseLedger);
            CookieSession session = login(syntheticEmail, password);
            cnpj = syntheticCnpj();
            oscId = registerSyntheticOrganization(session, cnpj, evaluation.representationType());
            caseLedger.put("oscId", oscId);
            JsonNode applicantView = json(mockMvc.perform(get("/osc/" + oscId + "/verificacao")
                    .cookie(session.cookies())).andReturn(), 200);
            applicationId = applicantView.path("applicationId").asLong();
            assertTrue(applicationId > 0);
            caseLedger.put("applicationId", applicationId);

            List<Long> uploadedIds = new ArrayList<>();
            for (EvidenceSpec evidence : evaluation.evidence()) {
                byte[] pdf = syntheticPdf(evidence.lines().stream()
                        .map(line -> line.replace("%CNPJ%", cnpj)).toList());
                MvcResult upload = mockMvc.perform(multipart("/osc/" + oscId + "/verificacao/documentos")
                        .file(new MockMultipartFile("arquivo", evidence.filename(), "application/pdf", pdf))
                        .param("tipo", evidence.type()).cookie(session.cookies())
                        .header(session.csrfHeader(), session.csrfToken())).andReturn();
                assertEquals(201, upload.getResponse().getStatus());
                JsonNode uploadJson = objectMapper.readTree(upload.getResponse().getContentAsByteArray());
                JsonNode returnedEvidence = uploadJson.path("evidence");
                assertTrue(returnedEvidence.isArray() && !returnedEvidence.isEmpty());
                uploadedIds.add(returnedEvidence.get(returnedEvidence.size() - 1).path("id").asLong());
            }
            caseLedger.put("evidenceIds", uploadedIds);
            JsonNode submitted = json(mockMvc.perform(post("/osc/" + oscId + "/verificacao/enviar")
                    .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())).andReturn(), 200);
            assertEquals("PENDING", submitted.path("status").asText());
            long currentSubmissionId = jdbc.queryForObject("""
                    SELECT id FROM public.ngo_representative_verification_submissions
                     WHERE application_id=? AND version_number=1 AND submitted_at IS NOT NULL
                    """, Long.class, applicationId);
            long exactJobId = jdbc.queryForObject("""
                    SELECT id FROM public.ngo_representative_verification_ai_jobs
                     WHERE application_id=? AND submission_id=? AND analysis_profile='REPRESENTATION_V1'
                    """, Long.class, applicationId, currentSubmissionId);
            caseLedger.put("submissionId", currentSubmissionId);
            caseLedger.put("jobId", exactJobId);
            caseLedger.put("jobCountBeforeProvider", jdbc.queryForObject(
                    "SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                    Integer.class, applicationId));
            writeEvaluationCase(caseLedger);

            MvcResult duplicate = mockMvc.perform(post("/osc/" + oscId + "/verificacao/enviar")
                    .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())).andReturn();
            assertEquals(409, duplicate.getResponse().getStatus());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                    Integer.class, applicationId));
            assertEquals(0, jdbc.queryForObject("SELECT attempts FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                    Integer.class, exactJobId));

            if (++providerRequests > 4) fail("Live Gemini request budget exceeded.");
            ExecutorService isolatedExecutor = Executors.newSingleThreadExecutor();
            VerificacaoAnaliseOrchestrator.ProcessOutcome outcome;
            try {
                outcome = orchestrator.processSpecificJob(exactJobId, geminiAdapter, UUID.randomUUID(), isolatedExecutor);
            } finally {
                isolatedExecutor.shutdownNow();
            }
            if (outcome != VerificacaoAnaliseOrchestrator.ProcessOutcome.COMPLETED) {
                String sanitizedState = jdbc.queryForObject("""
                        SELECT status || ':' || coalesce(last_error_code,'NONE') || ':attempt=' || attempts
                          FROM public.ngo_representative_verification_ai_jobs WHERE id=?
                        """, String.class, exactJobId);
                caseLedger.put("actualProviderOutcome", sanitizedState);
                writeEvaluationCase(caseLedger);
                fail("Stopping live evaluation after one unsuccessful provider request for this exact synthetic job.");
            }

            Map<String, Object> saved = jdbc.queryForMap("""
                    SELECT j.status,j.last_error_code,j.provider,j.model,j.input_tokens,j.output_tokens,
                           j.result_summary::text AS result_summary,j.attempts,a.status AS application_status
                      FROM public.ngo_representative_verification_ai_jobs j
                      JOIN public.ngo_representative_verification_applications a ON a.id=j.application_id
                     WHERE j.id=?
                    """, exactJobId);
            JsonNode summary = objectMapper.readTree((String) saved.get("result_summary"));
            caseLedger.put("provider", saved.get("provider"));
            caseLedger.put("model", saved.get("model"));
            caseLedger.put("inputTokens", saved.get("input_tokens"));
            caseLedger.put("outputTokens", saved.get("output_tokens"));
            caseLedger.put("actualIdentity", summary.path("identityComparison").asText());
            caseLedger.put("actualDelegationChain", summary.path("delegationEvidenceChain").asText());
            caseLedger.put("actualValidity", summary.path("apparentValidity").asText());
            caseLedger.put("conclusion", summary.path("conclusion").asText());
            caseLedger.put("applicationStatus", saved.get("application_status"));
            caseLedger.put("attempts", saved.get("attempts"));
            caseLedger.put("safeSummary", summary);
            writeEvaluationCase(caseLedger);

            assertEquals("COMPLETED", saved.get("status"));
            assertEquals(1, saved.get("attempts"));
            assertEquals("PENDING", saved.get("application_status"), "AI must never approve an application.");
            assertEquals(evaluation.expectedIdentity(), summary.path("identityComparison").asText());
            assertEquals(evaluation.expectedDelegationChain(), summary.path("delegationEvidenceChain").asText());
            assertEquals(evaluation.expectedValidity(), summary.path("apparentValidity").asText());
            assertNotEquals("EVIDENCIA_COMPATIVEL", summary.path("conclusion").asText(),
                    "The synthetic document evaluation remains advisory and never proves independent authority.");
            assertNotEquals("INDEPENDENTLY_VERIFIED", summary.path("identityComparison").asText());
            assertFalse(summary.toString().contains(evaluation.legalName()));
            if (evaluation.socialName() != null) assertFalse(summary.toString().contains(evaluation.socialName()));
            assertEquals("REPRESENTATION_FINDINGS_V2", jdbc.queryForObject(
                    "SELECT result_schema_version FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                    String.class, exactJobId));
            assertEquals("PENDING", jdbc.queryForObject(
                    "SELECT status FROM public.ngo_representative_verification_applications WHERE id=?",
                    String.class, applicationId));

            MvcResult afterCompletionDuplicate = mockMvc.perform(post("/osc/" + oscId + "/verificacao/enviar")
                    .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())).andReturn();
            assertEquals(409, afterCompletionDuplicate.getResponse().getStatus());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                    Integer.class, applicationId));
            assertEquals(1, jdbc.queryForObject("SELECT attempts FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                    Integer.class, exactJobId));
            caseLedger.put("providerRequestsUsed", providerRequests);
            writeEvaluationCase(caseLedger);
        }
        ledger.put("identityEvaluationCases", identityEvaluationCases);
        ledger.put("providerRequestsUsed", providerRequests);
        writeLedger();
        System.out.println("NGO_IDENTITY_GEMINI_LEDGER=" + ledgerPath);
        System.out.println("NGO_IDENTITY_GEMINI_CASES=" + objectMapper.writeValueAsString(identityEvaluationCases));
    }

    @Test
    void recordsTheIdOfThePriorSyntheticApplicantWhoseLoginWasBlockedBySharedIpAbuseLimits() {
        Assumptions.assumeTrue(Boolean.getBoolean("ngo.ai.ledger-recovery"),
                "This read-only test records only the applicant fixture created by this run before login.");
        List<Integer> ids = jdbc.query("""
                SELECT id FROM public.usuarios
                 WHERE nome='Amara Beatriz Synthetic Costa' AND nome_social='Amara Costa'
                   AND email LIKE 'ngo-identity-%@example.invalid'
                 ORDER BY id
                """, (row, number) -> row.getInt(1));
        assertEquals(1, ids.size(), "The task-owned synthetic applicant must be uniquely identifiable.");
        String runId = "identity-live-run-3ae53d52-ac36-4ca7-a22a-1243cd424e7d";
        ledgerPath = ledgerPath(runId);
        ledger.put("runId", runId);
        ledger.put("fixtureKind", "synthetic identity evaluation; login failed before NGO creation or Gemini processing");
        ledger.put("applicantUserId", ids.getFirst());
        ledger.put("accountStatus", jdbc.queryForObject(
                "SELECT ativo FROM public.usuarios WHERE id=?", Boolean.class, ids.getFirst()));
        writeLedger();
        System.out.println("NGO_IDENTITY_FIXTURE_RECOVERY_LEDGER=" + ledgerPath);
        System.out.println("NGO_IDENTITY_FIXTURE_RECOVERY_USER_ID=" + ids.getFirst());
    }

    @Test
    void retriesOnlyTheExplicitTimedOutSyntheticEvaluationJobOnce() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("NGO_VERIFICATION_SUPABASE_TEST_CONFIRMED")));
        long exactJobId = Long.getLong("ngo.ai.live-job-id", -1L);
        Assumptions.assumeTrue(exactJobId > 0, "No exact timed-out synthetic job ID was supplied.");
        assertTrue(applicationContext.containsBean("ngoVerificationAiExecutor"),
                "The evaluation scope can use the bounded executor for one exact ID only.");
        assertFalse(applicationContext.containsBean("verificacaoAnaliseWorker"),
                "The general queue worker must stay disabled.");
        String currentStatus = jdbc.queryForObject(
                "SELECT status FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                String.class, exactJobId);
        VerificacaoAnaliseOrchestrator.ProcessOutcome outcome = VerificacaoAnaliseOrchestrator.ProcessOutcome.COMPLETED;
        if (!"COMPLETED".equals(currentStatus)) {
            assertTrue(Boolean.getBoolean("ngo.ai.allow-single-live-request"),
                    "An explicit flag is required before this test can make one provider request.");
            ExecutorService isolatedExecutor = Executors.newSingleThreadExecutor();
            try {
                outcome = orchestrator.processSpecificJob(exactJobId, geminiAdapter, UUID.randomUUID(), isolatedExecutor);
            } finally {
                isolatedExecutor.shutdownNow();
            }
        }
        if (outcome != VerificacaoAnaliseOrchestrator.ProcessOutcome.COMPLETED) {
            String safeState = jdbc.queryForObject("""
                    SELECT status || ':' || coalesce(last_error_code,'NONE') || ':attempt=' || attempts
                      FROM public.ngo_representative_verification_ai_jobs WHERE id=?
                    """, String.class, exactJobId);
            System.out.println("NGO_GEMINI_RETRY_OUTCOME=" + safeState);
            fail("The one permitted explicit retry did not complete; no additional provider request will be made.");
        }
        Map<String, Object> saved = jdbc.queryForMap("""
                SELECT j.application_id,j.provider,j.model,j.input_tokens,j.output_tokens,j.result_summary::text,
                       j.attempts,a.current_submission_version,a.status AS application_status
                  FROM public.ngo_representative_verification_ai_jobs j
                  JOIN public.ngo_representative_verification_applications a ON a.id=j.application_id
                 WHERE j.id=?
                """, exactJobId);
        assertEquals("gemini", saved.get("provider"));
        assertEquals("gemini-3.1-flash-lite", saved.get("model"));
        assertEquals(2, saved.get("attempts"));
        assertEquals("PENDING", saved.get("application_status"), "AI output must not approve the application.");
        JsonNode summary = objectMapper.readTree((String) saved.get("result_summary"));
        assertNotEquals("EVIDENCIA_COMPATIVEL", summary.path("conclusion").asText(),
                "Only a human reviewer can decide this application; document consistency is not verification.");
        var readable = orchestrator.latestForAdmin((Long) saved.get("application_id"),
                (Integer) saved.get("current_submission_version")).orElseThrow();
        assertEquals("COMPLETED", readable.status());
        assertEquals(saved.get("input_tokens"), readable.inputTokens());
        assertEquals(saved.get("output_tokens"), readable.outputTokens());
        System.out.println("NGO_GEMINI_TEST_RESULT=" + objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("jobId", exactJobId), Map.entry("applicationId", saved.get("application_id")),
                Map.entry("attempts", saved.get("attempts")), Map.entry("provider", saved.get("provider")),
                Map.entry("model", saved.get("model")), Map.entry("inputTokens", saved.get("input_tokens")),
                Map.entry("outputTokens", saved.get("output_tokens")),
                Map.entry("conclusion", summary.path("conclusion").asText()),
                Map.entry("fields", summary.path("fields")), Map.entry("discrepancies", summary.path("discrepancies")),
                Map.entry("applicationStatus", saved.get("application_status")))));
    }

    @AfterEach
    void deactivateOnlySyntheticApplicant() {
        for (Integer syntheticUserId : syntheticUserIds) {
            userSessions.revogarSessoesDoUsuario(syntheticUserId);
            jdbc.update("UPDATE public.usuarios SET ativo=FALSE WHERE id=?", syntheticUserId);
        }
        if (!syntheticUserIds.isEmpty()) {
            for (Map<String, Object> evaluation : identityEvaluationCases) {
                if (evaluation.containsKey("applicantUserId")) evaluation.put("applicantAccountStatusAfterTest", "INACTIVE");
            }
            ledger.put("syntheticApplicantAccountStatusAfterTest", "INACTIVE");
        }
        if (ledgerPath != null) writeLedger();
    }

    private int createSyntheticUser(String syntheticEmail, String password) {
        return createSyntheticUser(syntheticEmail, password, "Conta sintética análise documental", null);
    }

    private int createSyntheticUser(String syntheticEmail, String password, String legalName, String socialName) {
        Integer id = jdbc.queryForObject("""
                INSERT INTO public.usuarios(cpf,data_nascimento,nome,nome_social,genero,email,telefone,senha_hash,ativo,
                                            email_verificado,telefone_verificado,data_criacao)
                VALUES(?,?,?,?, 'PREFIRO_NAO_INFORMAR', ?,NULL,?,TRUE,TRUE,FALSE,CURRENT_TIMESTAMP) RETURNING id
                """, Integer.class, syntheticCpf(), LocalDate.of(1990, 1, 1),
                legalName, socialName, syntheticEmail, passwordEncoder.encode(password));
        Integer roleId = jdbc.queryForObject("SELECT id FROM public.roles WHERE nome='USUARIO'", Integer.class);
        jdbc.update("INSERT INTO public.usuario_roles(id_usuario,id_role) VALUES(?,?)", id, roleId);
        syntheticUserIds.add(id);
        return id;
    }

    private int registerSyntheticOrganization(CookieSession session, String syntheticCnpj) throws Exception {
        return registerSyntheticOrganization(session, syntheticCnpj, "LEGAL_REPRESENTATIVE");
    }

    private int registerSyntheticOrganization(CookieSession session, String syntheticCnpj,
                                             String representationType) throws Exception {
        Map<String, Object> request = Map.ofEntries(
                Map.entry("cnpj", syntheticCnpj), Map.entry("causaPrincipal", "EDUCACAO"),
                Map.entry("causasSecundarias", List.of()), Map.entry("ods", List.of(4)),
                Map.entry("nome", ORGANIZATION_NAME), Map.entry("descricaoCurta", "Synthetic test entity."),
                Map.entry("descricao", "Fictitious organization created only for the Supabase TEST workflow."),
                Map.entry("anoFundacao", 2010), Map.entry("email", "ngo-ai-" + UUID.randomUUID() + "@example.invalid"),
                Map.entry("areaGeografica", "cidade"), Map.entry("pais", "Brasil"), Map.entry("estado", "SP"),
                Map.entry("cidade", "Cidade Sintetica QA"), Map.entry("cep", "00000-000"),
                Map.entry("bairro", "Bairro de Teste"), Map.entry("rua", "Rua Ficticia"), Map.entry("numero", "1"),
                Map.entry("atendimentoPresencial", false), Map.entry("atendimentoOnline", true),
                Map.entry("necessitaAgendamento", false), Map.entry("aceitaVoluntarios", true),
                Map.entry("processoSeletivoVoluntario", false), Map.entry("emiteCertificadoVoluntario", false));
        MvcResult result = mockMvc.perform(post("/osc/create")
                .queryParam("representacao", representationType)
                .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andReturn();
        return json(result, 201).path("id").asInt();
    }
    private CookieSession login(String loginEmail, String password) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/usuario/csrf")).andReturn();
        assertEquals(200, csrfResult.getResponse().getStatus());
        JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        MvcResult login = mockMvc.perform(post("/usuario/login")
                .with(request -> {
                    int address = MOCK_IP_SEQUENCE.getAndIncrement();
                    request.setRemoteAddr("198.18." + ((address / 254) % 2) + "." + ((address % 254) + 1));
                    return request;
                })
                .cookie(csrfCookie)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("email", loginEmail, "senha", password, "lembrarDeMim", false))))
                .andReturn();
        assertEquals(200, login.getResponse().getStatus(), "Synthetic account login uses the normal session path; "
                + safeLoginFailureCategory(login.getResponse().getContentAsString()));
        Cookie access = login.getResponse().getCookie("PA_ACCESS");
        Cookie refresh = login.getResponse().getCookie("PA_REFRESH");
        assertNotNull(access);
        return new CookieSession(csrfCookie, csrf.path("headerName").asText(), csrf.path("token").asText(), access, refresh);
    }

    private JsonNode json(MvcResult result, int expectedStatus) throws Exception {
        assertEquals(expectedStatus, result.getResponse().getStatus(),
                "Normal verification HTTP endpoint returned an unexpected status.");
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String safeLoginFailureCategory(String responseBody) {
        String body = responseBody == null ? "" : responseBody.toLowerCase(java.util.Locale.ROOT);
        if (body.contains("email ou senha incorretos")) return "credential rejection (sanitized)";
        if (body.contains("limite") || body.contains("tentativas")) return "login abuse limit (sanitized)";
        if (body.contains("email inválido")) return "synthetic email validation rejection (sanitized)";
        if (body.contains("csrf")) return "CSRF rejection (sanitized)";
        return "HTTP error category unavailable; response content intentionally redacted";
    }

    private byte[] syntheticPdf() throws Exception {
        return syntheticPdf(List.of(
                "SYNTHETIC TEST DOCUMENT ONLY - NO REAL PERSONAL DATA",
                "Organization: Associacao Ficticia Cedar Test",
                "CNPJ: " + syntheticCurrentCnpj(),
                "Fictional appointment evidence, no real signature or person"));
    }

    private byte[] syntheticPdf(List<String> lines) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.newLineAtOffset(40, 740);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -20);
                }
                content.endText();
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private String syntheticCurrentCnpj() { return jdbc.queryForObject("SELECT cnpj FROM public.oscs WHERE id=?", String.class, oscId); }

    private void writeLedger() {
        try {
            if (ledgerPath == null) {
                String configured = System.getenv("NGO_AI_FIXTURE_LEDGER_PATH");
                ledgerPath = configured == null || configured.isBlank()
                        ? Path.of(System.getProperty("java.io.tmpdir"), "ngo-ai-test-ledger-" + UUID.randomUUID() + ".json")
                        : Path.of(configured);
            }
            Files.createDirectories(ledgerPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(ledgerPath.toFile(), ledger);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not preserve the synthetic fixture ID ledger.");
        }
    }

    private void writeEvaluationCase(Map<String, Object> caseLedger) {
        ledger.put("identityEvaluationCases", identityEvaluationCases);
        writeLedger();
    }

    private Path ledgerPath(String runId) {
        String configured = System.getenv("NGO_AI_FIXTURE_LEDGER_PATH");
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        return Path.of(System.getProperty("java.io.tmpdir"), "ngo-ai-test-ledger-" + runId + ".json");
    }

    private static HttpServer startExternalMock() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", NgoVerificationGeminiSupabaseIntegrationTest::answerExternalMock);
            server.start();
            return server;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void answerExternalMock(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body;
        if (path.startsWith("/office/")) {
            CNPJA_MOCK_CALLS.incrementAndGet();
            String taxId = path.substring("/office/".length());
            body = "{\"taxId\":\"" + taxId + "\",\"alias\":\"ASSOCIACAO FICTICIA CEDAR TEST\","
                    + "\"founded\":\"2010-01-01\",\"status\":{\"text\":\"Ativa\"},"
                    + "\"company\":{\"name\":\"ASSOCIACAO FICTICIA CEDAR TEST\","
                    + "\"nature\":{\"id\":\"3999\",\"text\":\"Associacao Privada Ficticia\"}}}";
        } else if (path.equals("/search")) {
            GEOCODING_MOCK_CALLS.incrementAndGet();
            body = "[{\"lat\":\"0\",\"lon\":\"0\"}]";
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static String syntheticCpf() {
        String base = String.format("%09d", RANDOM.nextInt(100_000_000, 999_999_999));
        int first = cpfDigit(base, 10);
        String withFirst = base + first;
        return withFirst + cpfDigit(withFirst, 11);
    }

    private static int cpfDigit(String value, int weight) {
        int sum = 0;
        for (int i = 0; i < value.length(); i++) sum += (value.charAt(i) - '0') * (weight - i);
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }

    private static String syntheticCnpj() {
        String base = String.format("%08d0001", RANDOM.nextInt(10_000_000, 99_999_999));
        int[] firstWeights = {5,4,3,2,9,8,7,6,5,4,3,2};
        int[] secondWeights = {6,5,4,3,2,9,8,7,6,5,4,3,2};
        int first = cnpjDigit(base, firstWeights);
        String withFirst = base + first;
        return withFirst + cnpjDigit(withFirst, secondWeights);
    }

    private static int cnpjDigit(String value, int[] weights) {
        int total = 0;
        for (int index = 0; index < weights.length; index++) total += (value.charAt(index) - '0') * weights[index];
        int remainder = total % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private record CookieSession(Cookie csrfCookie, String csrfHeader, String csrfToken, Cookie access, Cookie refresh) {
        Cookie[] cookies() { return new Cookie[]{csrfCookie, access, refresh}; }
    }
    private record IdentityEvaluationCase(String name, String representationType, String legalName,
                                          String socialName, String applicantDocumentName,
                                          List<EvidenceSpec> evidence, String expectedIdentity,
                                          String expectedDelegationChain, String expectedValidity) { }
    private record EvidenceSpec(String type, String filename, List<String> lines) { }
}
