package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.StripeClient;
import com.portoajuda.aplicacao_osc.admin.AdminAssignment;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentStatus;
import com.portoajuda.aplicacao_osc.admin.AdminOperationAuditService;
import com.portoajuda.aplicacao_osc.admin.AdminProfile;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.dto.request.RequestModerationPointAdjustmentDTO;
import com.portoajuda.aplicacao_osc.controller.AdminSessionCookie;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository;
import com.portoajuda.aplicacao_osc.service.PrivateVerificationEvidenceStorage.SignedAccess;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.Email;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.Cookie;

/** Real app, TEST PostgreSQL and real private Storage API; external address/email/SMS providers are local mocks. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.maximum-pool-size=8",
        "app.ngo-verification.enabled=true",
        "app.ngo-verification.ai-analysis.enabled=false",
        "app.ngo-verification.ai-analysis.results-read-enabled=true",
        "app.ngo-verification.ai-analysis.worker-enabled=false",
        "app.ngo-verification.ai-analysis.evaluation-worker-enabled=false",
        "app.ngo-verification.auto-approval.enabled=false",
        "RESEND_API_KEY=test-placeholder-no-send",
        "app.moderation.points.enabled=true",
        "app.admin.rbac.enforcement-enabled=true",
        "app.initialization.seed-roles=false",
        "app.maintenance.cleanup-cron=-",
        "app.admin-auth.controls-cleanup-cron=-",
        "app.admin-auth.delivery-worker.enabled=false",
        "admin-auth.delivery-poll-ms=3600000",
        "app.cookies.secure=false",
        "ALLOWED_ORIGINS=http://localhost:8082"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(NgoVerificationSupabaseTargetCondition.class)
class NgoVerificationSupabaseFullIntegrationTest {
    private static final HttpServer EXTERNAL_MOCK = startExternalMock();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AdminAuthChallengeRepository adminChallenges;
    @Autowired AdminAuthCodeSecurity adminAuthCodeSecurity;
    @Autowired com.portoajuda.aplicacao_osc.repository.UsuarioRepository usuarios;
    @Autowired AdminAssignmentRepository assignments;
    @Autowired AdminOperationAuditService operationAudit;
    @Autowired AdminPrivilegedSessionService adminSessions;
    @Autowired ModerationPointsService moderationPoints;
    @Autowired SessaoUsuarioService userSessions;
    @Autowired VerificacaoRepresentanteJdbcRepository verificationRepository;
    @MockitoSpyBean PrivateVerificationEvidenceStorage storage;
    @MockitoSpyBean VerificacaoRepresentanteJdbcRepository verificationRepositorySpy;
    @MockitoBean AnaliseRepresentacaoDocumental analysisProvider;
    @MockitoBean EmailService emailService;
    @MockitoBean SmsService smsService;
    @MockitoBean StripeClient stripeClient;
    @LocalServerPort int port;
    @Value("${supabase.url}") String supabaseUrl;
    @Value("${supabase.secret-key}") String supabaseSecretKey;
    @Value("${supabase.bucket-verificacao}") String evidenceBucket;

    private final Map<String, Object> fixtureLedger = new LinkedHashMap<>();
    private Integer applicantId;
    private Integer crossUserId;
    private Integer reviewerId;
    private UUID reviewerAssignmentId;
    private Integer oscId;
    private Long applicationId;
    private Long failedUploadObjectKeyPresent;
    private final List<Long> evidenceIds = new ArrayList<>();
    private final List<String> retainedObjectKeys = new ArrayList<>();
    private final Map<Integer, String> syntheticEmails = new HashMap<>();
    private CookieSession applicantSession;
    private CookieSession crossUserSession;
    private CookieSession reviewerSession;

    @DynamicPropertySource
    static void syntheticExternalServices(DynamicPropertyRegistry properties) {
        String base = "http://127.0.0.1:" + EXTERNAL_MOCK.getAddress().getPort();
        properties.add("cnpja.url-base", () -> base + "/office/");
        properties.add("app.geocoding.base-url", () -> base);
    }

    @AfterAll
    static void stopSyntheticAddressMock() {
        EXTERNAL_MOCK.stop(0);
    }

    @Test
    void fullVerificationWorkflowUsesSyntheticAccountsAndPrivateStorage() throws Exception {
        assertSupabaseTestDatasource();
        assertEquals("false", System.getenv().getOrDefault("NGO_VERIFICATION_ENABLED", "false").toLowerCase());
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM public.admin_auth_deliveries d
                JOIN public.admin_auth_challenges c ON c.id=d.challenge_id
                WHERE c.state='ISSUING' AND c.expires_at>CURRENT_TIMESTAMP
                  AND d.state IN ('PENDING','PROCESSING')
                """, Integer.class), "Do not let a test worker process any pre-existing challenge.");
        assertTrue(jdbc.queryForObject("""
                SELECT count(*) FROM public.ngo_representative_verification_legacy_exemptions l
                 WHERE public.ngo_representative_is_publicly_eligible(l.osc_id)
                """, Integer.class) >= 1, "Existing legacy NGOs must remain exempt and publicly eligible subject to their other rules.");
        assertEquals("PROPOSED", jdbc.queryForObject(
                "SELECT retention_policy_status FROM public.ngo_representative_verification_config WHERE id=1", String.class));

        String applicantPassword = syntheticPassword();
        String crossUserPassword = syntheticPassword();
        String reviewerPassword = syntheticPassword();
        applicantId = createSyntheticUser("USUARIO", applicantPassword, null);
        updateLedger("applicantUserId", applicantId);
        crossUserId = createSyntheticUser("USUARIO", crossUserPassword, null);
        updateLedger("crossUserId", crossUserId);
        reviewerId = createSyntheticUser("ADMIN", reviewerPassword, "+5511999999999");
        updateLedger("reviewerUserId", reviewerId);
        reviewerAssignmentId = createSyntheticSuperAdminAssignment(reviewerId);
        updateLedger("reviewerAssignmentId", reviewerAssignmentId.toString());

        applicantSession = login(syntheticEmails.get(applicantId), applicantPassword);
        crossUserSession = login(syntheticEmails.get(crossUserId), crossUserPassword);
        reviewerSession = issueSyntheticAdminSession(syntheticEmails.get(reviewerId));

        String cnpj = syntheticCnpj();
        String ngoEmail = "ngo-" + UUID.randomUUID() + "@example.invalid";
        MvcResult created = mockMvc.perform(post("/osc/create")
                .queryParam("representacao", "LEGAL_REPRESENTATIVE")
                .cookie(applicantSession.cookies())
                .header(applicantSession.csrfHeader(), applicantSession.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(newNgoRequest(cnpj, ngoEmail))))
            .andReturn();
        assertEquals(201, created.getResponse().getStatus(), "Synthetic NGO registration must traverse the real MVC/security/JPA chain.");
        oscId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).path("id").asInt();
        assertTrue(oscId > 0);
        updateLedger("oscId", oscId);

        JsonNode applicantView = json(mockMvc.perform(get("/osc/" + oscId + "/verificacao")
                .cookie(applicantSession.cookies())).andReturn());
        applicationId = applicantView.path("applicationId").asLong();
        assertTrue(applicationId > 0);
        assertEquals("PENDING", applicantView.path("status").asText());
        updateLedger("initialApplicationId", applicationId);
        assertEquals("INATIVA", jdbc.queryForObject("SELECT status FROM public.oscs WHERE id=?", String.class, oscId));
        assertFalse(verificationRepository.isPubliclyEligible(oscId));
        assertTrue(mockMvc.perform(get("/osc/" + oscId)).andReturn().getResponse().getStatus() >= 400,
                "A pending inactive ONG must remain hidden from public detail.");

        long activeEvidenceBefore = evidenceCount(applicationId);
        upload(applicantSession, oscId, "application/pdf", "comprovante-sintetico.pdf", pdfBytes());
        upload(applicantSession, oscId, "image/jpeg", "comprovante-sintetico.jpg", jpegBytes());
        upload(applicantSession, oscId, "image/png", "comprovante-sintetico.png", pngBytes());
        assertEquals(activeEvidenceBefore + 3, evidenceCount(applicationId));
        recordEvidenceLedger(applicationId);

        long beforePersistenceFailure = evidenceCount(applicationId);
        AtomicReference<String> failedUploadKey = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            String key = (String) invocation.callRealMethod();
            failedUploadKey.set(key);
            return key;
        }).when(storage).store(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any(byte[].class));
        Mockito.doThrow(new IllegalStateException("Synthetic evidence persistence failure."))
                .when(verificationRepositorySpy).insertEvidence(Mockito.anyLong(), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyInt());
        assertThrows(jakarta.servlet.ServletException.class, () -> uploadResult(applicantSession, oscId, "application/pdf",
                "falha-sintetica.pdf", pdfBytes()),
                "A simulated database persistence failure should abort the upload request.");
        assertEquals(beforePersistenceFailure, evidenceCount(applicationId),
                "The failed database transaction must not leave an evidence row.");
        assertNotNull(failedUploadKey.get(), "The failure test must have created a task-owned Storage object first.");
        updateLedger("failedUploadObjectKey", failedUploadKey.get());
        assertThrows(IllegalStateException.class, () -> storage.createShortLivedAccess(failedUploadKey.get()),
                "Rollback cleanup must delete the newly uploaded task-owned object.");
        Mockito.reset(storage, verificationRepositorySpy);

        long beforeInvalid = evidenceCount(applicationId);
        MvcResult invalid = uploadResult(applicantSession, oscId, "image/png", "nao-e-png.png", "fake-image".getBytes(StandardCharsets.UTF_8));
        assertTrue(invalid.getResponse().getStatus() >= 400 && invalid.getResponse().getStatus() < 500);
        MvcResult oversized = uploadResult(applicantSession, oscId, "application/pdf", "grande.pdf",
                new byte[10 * 1024 * 1024 + 1]);
        assertTrue(oversized.getResponse().getStatus() >= 400 && oversized.getResponse().getStatus() < 500);
        assertEquals(beforeInvalid, evidenceCount(applicationId), "Invalid/oversized payloads must be rejected before Storage persistence.");

        JsonNode evidenceRows = json(mockMvc.perform(get("/osc/" + oscId + "/verificacao")
                .cookie(applicantSession.cookies())).andReturn()).path("evidence");
        assertTrue(evidenceRows.isArray() && evidenceRows.size() >= 3);
        long pdfEvidenceId = evidenceRows.get(0).path("id").asLong();
        MvcResult signed = mockMvc.perform(get("/osc/" + oscId + "/verificacao/documentos/" + pdfEvidenceId + "/acesso")
                .cookie(applicantSession.cookies())).andReturn();
        assertEquals(200, signed.getResponse().getStatus());
        JsonNode signedPayload = json(signed);
        String temporaryUrl = signedPayload.path("temporaryUrl").asText();
        assertFalse(temporaryUrl.isBlank());
        assertTrue(URI.create(temporaryUrl).getPath().startsWith("/storage/v1/object/sign/"),
                "The Storage API's relative signing path must be prefixed with /storage/v1.");
        assertTrue(signedPayload.hasNonNull("urlExpiresAt"));
        HttpResponse<byte[]> signedDownload = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(temporaryUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, signedDownload.statusCode(), "The real short-lived Storage link must download the fictitious PDF.");
        assertTrue(new String(signedDownload.body(), StandardCharsets.US_ASCII).startsWith("%PDF-"));

        MvcResult anonymousAppAccess = mockMvc.perform(get("/osc/" + oscId + "/verificacao/documentos/" + pdfEvidenceId + "/acesso"))
                .andReturn();
        assertEquals(401, anonymousAppAccess.getResponse().getStatus());
        assertEquals(404, mockMvc.perform(get("/osc/" + oscId + "/verificacao/documentos/" + pdfEvidenceId + "/acesso")
                .cookie(crossUserSession.cookies())).andReturn().getResponse().getStatus());
        String objectKey = jdbc.queryForObject("SELECT storage_key FROM public.ngo_representative_verification_evidence WHERE id=?",
                String.class, pdfEvidenceId);
        HttpResponse<byte[]> directAnonymousObject = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(supabaseUrl + "/storage/v1/object/" + evidenceBucket + "/" + objectKey))
                        .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertFalse(directAnonymousObject.statusCode() >= 200 && directAnonymousObject.statusCode() < 300,
                "The private bucket must deny direct anonymous object access.");

        submit(applicantSession, oscId);
        long reviewApplicationId = applicationId;
        startReview(reviewerSession, reviewApplicationId);
        decide(reviewerSession, reviewApplicationId, "REQUEST_INFORMATION", "Envie um documento fictício adicional para conferência.");
        upload(applicantSession, oscId, "image/png", "correcao-sintetica.png", pngBytes());
        submit(applicantSession, oscId);
        startReview(reviewerSession, reviewApplicationId);
        decide(reviewerSession, reviewApplicationId, "REJECT", "A evidência fictícia não demonstra a representação declarada.");

        MvcResult reapplied = mockMvc.perform(post("/osc/" + oscId + "/verificacao/reaplicar")
                .cookie(applicantSession.cookies())
                .header(applicantSession.csrfHeader(), applicantSession.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"representationType\":\"AUTHORIZED_DELEGATE\",\"confirmNewApplication\":true}"))
            .andReturn();
        assertEquals(201, reapplied.getResponse().getStatus());
        applicationId = json(reapplied).path("applicationId").asLong();
        assertNotEquals(reviewApplicationId, applicationId);
        updateLedger("reapplicationId", applicationId);
        upload(applicantSession, oscId, "application/pdf", "novo-ciclo-ficticio.pdf", pdfBytes());
        MvcResult delegationEvidence = uploadResult(applicantSession, oscId, "DELEGATION_AUTHORIZATION",
                "autorizacao-ficticia.pdf", "application/pdf", pdfBytes());
        assertEquals(201, delegationEvidence.getResponse().getStatus(),
                "An authorized-delegate application requires both representation and delegation evidence.");
        for (JsonNode evidence : json(delegationEvidence).path("evidence")) {
            long evidenceId = evidence.path("id").asLong();
            if (evidenceId > 0 && !evidenceIds.contains(evidenceId)) evidenceIds.add(evidenceId);
        }
        recordEvidenceLedger(applicationId);
        submit(applicantSession, oscId);
        startReview(reviewerSession, applicationId);
        decide(reviewerSession, applicationId, "REJECT", "A nova evidência ainda não comprova os poderes da pessoa delegada.");

        HttpResponse<String> reopened = adminRequest(reviewerSession, "POST",
                "/admin/ngo-verifications/" + applicationId + "/reabrir",
                "{\"justification\":\"O requerente pode corrigir a divergência documental identificada.\"}");
        assertEquals(200, reopened.statusCode());
        assertEquals("INFORMATION_REQUESTED", objectMapper.readTree(reopened.body()).path("status").asText());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_events WHERE application_id=? AND action='REJECTED'",
                Integer.class, applicationId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_events WHERE application_id=? AND action='ADMIN_REOPENED'",
                Integer.class, applicationId));
        upload(applicantSession, oscId, "image/jpeg", "correcao-final-sintetica.jpg", jpegBytes());
        MvcResult reopenedDelegationEvidence = uploadResult(applicantSession, oscId, "DELEGATION_AUTHORIZATION",
                "autorizacao-final-ficticia.pdf", "application/pdf", pdfBytes());
        assertEquals(201, reopenedDelegationEvidence.getResponse().getStatus());
        for (JsonNode evidence : json(reopenedDelegationEvidence).path("evidence")) {
            long evidenceId = evidence.path("id").asLong();
            if (evidenceId > 0 && !evidenceIds.contains(evidenceId)) evidenceIds.add(evidenceId);
        }
        recordEvidenceLedger(applicationId);
        submit(applicantSession, oscId);
        startReview(reviewerSession, applicationId);

        // Simulate separate, pre-existing operational restrictions on this synthetic target.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/osc/" + oscId)
                .cookie(applicantSession.cookies())
                .header(applicantSession.csrfHeader(), applicantSession.csrfToken()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        UUID pointsReference = UUID.randomUUID();
        HttpResponse<String> points = adminRequest(reviewerSession, "POST",
                "/admin/moderation/subjects/USER/" + applicantId + "/points",
                objectMapper.writeValueAsString(Map.of("referenceKey", pointsReference, "delta", 10,
                        "justificativa", "Ajuste sintético para verificar preservação de sanção.")));
        assertEquals(204, points.statusCode());
        updateLedger("moderationReferenceKey", pointsReference.toString());
        jdbc.update("""
                INSERT INTO public.content_moderation_blocks(target_type,target_id,content_kind,source,active,reason,created_by_user_id)
                VALUES('OSC',?,'OSC','ADMIN_RESTRICTION',TRUE,'Bloqueio sintético de teste',?)
                """, oscId, reviewerId);
        long permanentBefore = jdbc.queryForObject("SELECT count(*) FROM public.moderation_point_restrictions WHERE subject_user_id=? AND permanent_active",
                Long.class, applicantId);
        assertEquals(1, permanentBefore);

        decide(reviewerSession, applicationId, "APPROVE", "A documentação fictícia final comprova a representação declarada.");
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM public.ngo_representative_verification_applications WHERE id=?",
                String.class, applicationId));
        var orgState = jdbc.queryForMap("SELECT status,data_arquivamento FROM public.oscs WHERE id=?", oscId);
        assertEquals("INATIVA", orgState.get("status"));
        assertNotNull(orgState.get("data_arquivamento"));
        assertEquals(10L, jdbc.queryForObject("SELECT coalesce(sum(points_delta),0) FROM public.moderation_point_ledger WHERE subject_user_id=?",
                Long.class, applicantId));
        assertEquals(permanentBefore, jdbc.queryForObject("SELECT count(*) FROM public.moderation_point_restrictions WHERE subject_user_id=? AND permanent_active",
                Long.class, applicantId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.content_moderation_blocks WHERE target_type='OSC' AND target_id=? AND source='ADMIN_RESTRICTION' AND active",
                Integer.class, oscId));
        assertEquals(401, mockMvc.perform(get("/osc/" + oscId + "/verificacao" )).andReturn().getResponse().getStatus());
        assertTrue(mockMvc.perform(get("/osc/" + oscId)).andReturn().getResponse().getStatus() >= 400,
                "Approval must not unarchive or activate this synthetic NGO.");
        assertEquals("PROPOSED", jdbc.queryForObject("SELECT retention_policy_status FROM public.ngo_representative_verification_config WHERE id=1",
                String.class), "The validation must not approve the retention policy.");

        // The evidence link is a bearer token: membership revocation blocks future app links,
        // but the already-issued signed URL is expected to remain usable until its 5-minute TTL.
        jdbc.update("DELETE FROM public.osc_membros WHERE id_osc=? AND id_usuario=?", oscId, applicantId);
        assertEquals(404, mockMvc.perform(get("/osc/" + oscId + "/verificacao/documentos/" + pdfEvidenceId + "/acesso")
                .cookie(applicantSession.cookies())).andReturn().getResponse().getStatus());
        assertEquals(200, HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(temporaryUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).statusCode());

        storageDeleteSmokeTest(oscId, applicationId);
        recordEvidenceLedger(applicationId);
        updateLedger("reapplicationId", jdbc.queryForObject("SELECT max(id) FROM public.ngo_representative_verification_applications WHERE osc_id=?",
                Long.class, oscId));
    }

    @Test
    void authorizedAdminDetailApiReturnsPersistedAdvisoryResultWithoutReprocessing() throws Exception {
        assertEquals("postgres", jdbc.queryForObject("SELECT current_database()", String.class));
        Map<String, Object> before = jdbc.queryForMap("""
                SELECT j.status,j.attempts,a.status AS application_status,a.current_submission_version,
                       s.version_number
                  FROM public.ngo_representative_verification_ai_jobs j
                  JOIN public.ngo_representative_verification_applications a ON a.id=j.application_id
                  JOIN public.ngo_representative_verification_submissions s ON s.id=j.submission_id
                 WHERE j.id=6 AND j.application_id=19
                """);
        assertEquals("COMPLETED", before.get("status"));
        assertEquals("PENDING", before.get("application_status"));
        assertEquals(401, mockMvc.perform(get("/admin/ngo-verifications/19")).andReturn()
                .getResponse().getStatus(), "Detail remains unavailable without an ADMIN_SESSION.");

        reviewerId = createSyntheticUser("ADMIN", syntheticPassword(), "+5511999999999");
        ledgerPut("fixturePurpose", "synthetic ADMIN reviewer only; read-only request for persisted application 19 analysis");
        ledgerPut("reviewerUserId", reviewerId);
        ledgerPut("targetApplicationId", 19);
        ledgerPut("targetJobId", 6);
        reviewerAssignmentId = createSyntheticSuperAdminAssignment(reviewerId);
        ledgerPut("reviewerAssignmentId", reviewerAssignmentId.toString());
        CookieSession admin = issueSyntheticAdminSession(syntheticEmails.get(reviewerId));

        HttpClient http = HttpClient.newHttpClient();
        HttpRequest detailRequest = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/admin/ngo-verifications/19"))
                .header("Cookie", AdminSessionCookie.NAME + "=" + admin.admin().getValue())
                .GET().build();
        HttpResponse<String> response = http.send(detailRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        JsonNode detail = objectMapper.readTree(response.body());
        assertEquals("PENDING", detail.path("status").asText());
        assertEquals(1, detail.path("submissionVersion").asInt());
        assertEquals("COMPLETED", detail.path("aiAnalysis").path("status").asText());
        assertEquals("DIVERGENCIA", detail.path("aiAnalysis").path("conclusion").asText());
        assertEquals("INSUFFICIENT_EVIDENCE", detail.path("aiAnalysis").path("identityComparison").asText());
        assertTrue(detail.path("aiAnalysis").path("discrepancies").toString()
                .contains("REPRESENTANTE_LEGAL_NAO_IDENTIFICADO"));
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> repeated = http.send(detailRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, repeated.statusCode());
            assertEquals("COMPLETED", objectMapper.readTree(repeated.body())
                    .path("aiAnalysis").path("status").asText());
        }
        Mockito.verifyNoInteractions(analysisProvider);
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=19
                """, Integer.class));

        Map<String, Object> after = jdbc.queryForMap("""
                SELECT j.status,j.attempts,a.status AS application_status,a.current_submission_version,
                       s.version_number
                  FROM public.ngo_representative_verification_ai_jobs j
                  JOIN public.ngo_representative_verification_applications a ON a.id=j.application_id
                  JOIN public.ngo_representative_verification_submissions s ON s.id=j.submission_id
                 WHERE j.id=6 AND j.application_id=19
                """);
        assertEquals(before, after, "Authorized detail reads must not claim/retry the job or decide the application.");
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM public.ngo_representative_verification_ai_job_events
                 WHERE job_id=6 AND event_type='CLAIMED'
                """, Integer.class));
        Mockito.verifyNoInteractions(analysisProvider, emailService, smsService, stripeClient);
        ledgerPut("authorizedAdminDetailHttpStatus", 200);
        ledgerPut("jobProcessingAfterRead", "unchanged; no provider request");
    }

    private void storageDeleteSmokeTest(int organizationId, long appId) throws Exception {
        String key = storage.store(organizationId, appId, 99, "pdf", "application/pdf", pdfBytes());
        updateLedger("disposableStorageObjectKey", key);
        SignedAccess link = storage.createShortLivedAccess(key);
        assertEquals(200, HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(link.url())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).statusCode());
        storage.delete(key);
        HttpResponse<byte[]> deleted = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(link.url())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertFalse(deleted.statusCode() >= 200 && deleted.statusCode() < 300,
                "A deleted task-owned Storage object must not remain downloadable.");
    }

    @AfterEach
    void cleanupSyntheticAccounts() throws IOException {
        Instant now = Instant.now();
        if (reviewerAssignmentId != null) {
            assignments.revoke(reviewerAssignmentId, null, "Encerramento sintético do teste de verificação.", now);
            operationAudit.recordLifecycle(null, "SYSTEM", AdminProfile.SUPER_ADMIN,
                    "SYNTHETIC_TEST_ASSIGNMENT_REVOKED", reviewerId.toString(),
                    "Revogação da atribuição criada exclusivamente para integração TEST.");
        }
        if (reviewerId != null) {
            adminSessions.revogarTodasDoUsuario(reviewerId, "ADMIN_REVOKED");
            jdbc.update("DELETE FROM public.usuario_roles WHERE id_usuario=? AND id_role=(SELECT id FROM public.roles WHERE nome='ADMIN')", reviewerId);
            jdbc.update("UPDATE public.usuarios SET ativo=FALSE WHERE id=?", reviewerId);
            ledgerPut("reviewerAccountStatusAfterCleanup", "INACTIVE");
            ledgerPut("reviewerAssignmentStatusAfterCleanup", "REVOKED");
        }
        if (applicantId != null) {
            userSessions.revogarSessoesDoUsuario(applicantId);
            jdbc.update("UPDATE public.usuarios SET ativo=FALSE WHERE id=?", applicantId);
        }
        if (crossUserId != null) {
            userSessions.revogarSessoesDoUsuario(crossUserId);
            jdbc.update("UPDATE public.usuarios SET ativo=FALSE WHERE id=?", crossUserId);
        }
    }

    private int createSyntheticUser(String roleName, String password, String phone) {
        String email = syntheticEmail("user");
        Integer id = jdbc.queryForObject("""
                INSERT INTO public.usuarios(cpf,data_nascimento,nome,genero,email,telefone,senha_hash,ativo,email_verificado,telefone_verificado,data_criacao)
                VALUES(?,?,?,'OUTRO',?,?,?,TRUE,TRUE,TRUE,CURRENT_TIMESTAMP) RETURNING id
                """, Integer.class, syntheticCpf(), LocalDate.of(1990, 1, 1), "Conta Sintética NGO Verification",
                email, phone, passwordEncoder.encode(password));
        syntheticEmails.put(id, email);
        Integer roleId = jdbc.queryForObject("SELECT id FROM public.roles WHERE nome=?", Integer.class, roleName);
        jdbc.update("INSERT INTO public.usuario_roles(id_usuario,id_role) VALUES(?,?)", id, roleId);
        return id;
    }

    private UUID createSyntheticSuperAdminAssignment(int userId) {
        Instant now = Instant.now();
        AdminAssignment assignment = new AdminAssignment(UUID.randomUUID(), userId, userId, AdminProfile.SUPER_ADMIN,
                AdminAssignmentStatus.ACTIVE, now, null, now, null, null, "Atribuição sintética de integração TEST.");
        assignments.insert(assignment);
        return assignment.id();
    }

    private CookieSession login(String email, String password) throws Exception {
        CookieSession csrf = csrfSession();
        MockHttpServletRequestBuilder login = post("/usuario/login")
                .cookie(csrf.cookies())
                .header(csrf.csrfHeader(), csrf.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("email", email, "senha", password, "lembrarDeMim", false)));
        MvcResult result = mockMvc.perform(login).andReturn();
        assertEquals(200, result.getResponse().getStatus(), "Synthetic account login must succeed through the actual security chain.");
        Cookie access = result.getResponse().getCookie("PA_ACCESS");
        Cookie refresh = result.getResponse().getCookie("PA_REFRESH");
        assertNotNull(access);
        return new CookieSession(csrf.csrfCookie(), csrf.csrfHeader(), csrf.csrfToken(), access, refresh, null);
    }

    private CookieSession issueSyntheticAdminSession(String email) throws Exception {
        CookieSession csrf = csrfSession();
        // Keep test authentication away from the shared delivery queue: a separate already-running
        // local application may own that queue. The real session service still persists the
        // credential and the HTTP filter/RBAC chain validates it for every administrative request.
        UUID challengeId = UUID.randomUUID();
        Instant now = Instant.now();
        AdminAuthChallenge challenge = new AdminAuthChallenge();
        challenge.setId(challengeId);
        challenge.setUsuario(usuarios.getReferenceById(reviewerId));
        challenge.setAccountKeyHash(adminAuthCodeSecurity.accountKey(email));
        challenge.setIpKeyHash(adminAuthCodeSecurity.ipKey("127.0.0.1"));
        challenge.setEmailSnapshotHash(adminAuthCodeSecurity.hashIdentifier("email", email));
        challenge.setPhoneSnapshotHash(adminAuthCodeSecurity.hashIdentifier("phone", "+5511999999999"));
        challenge.setEmailAttempts(0);
        challenge.setSmsAttempts(0);
        challenge.setFailedAttempts(0);
        challenge.setState(AdminAuthChallenge.State.VERIFIED);
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusSeconds(600));
        challenge.setEmailVerifiedAt(now);
        challenge.setSmsVerifiedAt(now);
        challenge.setCompletedAt(now);
        adminChallenges.saveAndFlush(challenge);
        AdminPrivilegedSessionService.IssuedCredential issued = adminSessions.emitir(challengeId, "127.0.0.1");
        assertTrue(adminSessions.autenticar(issued.rawCredential(), "127.0.0.1").isPresent(),
                "The synthetic persisted ADMIN_SESSION must pass service-side validation.");
        Cookie admin = new Cookie(AdminSessionCookie.NAME, issued.rawCredential());
        CookieSession result = new CookieSession(csrf.csrfCookie(), csrf.csrfHeader(), csrf.csrfToken(), null, null, admin);
        HttpResponse<Void> sessionCheck = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/admin/auth/session"))
                        .header("Cookie", AdminSessionCookie.NAME + "=" + issued.rawCredential())
                        .GET().build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(204, sessionCheck.statusCode(), "The real network HTTP chain must accept the persisted synthetic ADMIN_SESSION.");
        return result;
    }

    private CookieSession csrfSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/usuario/csrf")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = json(result);
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return new CookieSession(cookie, body.path("headerName").asText(), body.path("token").asText(), null, null, null);
    }

    private void upload(CookieSession session, int organizationId, String mediaType, String filename, byte[] bytes) throws Exception {
        MvcResult result = uploadResult(session, organizationId, mediaType, filename, bytes);
        assertEquals(201, result.getResponse().getStatus(), "Fictitious evidence should upload to the private Storage bucket.");
        JsonNode body = json(result);
        JsonNode evidence = body.path("evidence");
        if (!evidence.isArray()) evidence = body.path("evidencias");
        assertTrue(evidence.isArray());
        for (JsonNode item : evidence) {
            long id = item.path("id").asLong();
            if (id > 0 && !evidenceIds.contains(id)) evidenceIds.add(id);
        }
        recordEvidenceLedger(applicationId);
    }

    private MvcResult uploadResult(CookieSession session, int organizationId, String mediaType, String filename, byte[] bytes) throws Exception {
        return uploadResult(session, organizationId, "REPRESENTATION_PROOF", filename, mediaType, bytes);
    }

    private MvcResult uploadResult(CookieSession session, int organizationId, String documentType,
                                   String filename, String mediaType, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("arquivo", filename, mediaType, bytes);
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/osc/" + organizationId + "/verificacao/documentos")
                .param("tipo", documentType).file(file).cookie(session.cookies())
                .header(session.csrfHeader(), session.csrfToken()))
            .andReturn();
    }

    private void submit(CookieSession session, int organizationId) throws Exception {
        assertEquals(200, mockMvc.perform(post("/osc/" + organizationId + "/verificacao/enviar")
                .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())).andReturn().getResponse().getStatus());
    }

    private void startReview(CookieSession reviewer, long id) throws Exception {
        assertEquals(200, adminRequest(reviewer, "POST", "/admin/ngo-verifications/" + id + "/iniciar", null)
                .statusCode());
    }

    private void decide(CookieSession reviewer, long id, String decision, String justification) throws Exception {
        HttpResponse<String> result = adminRequest(reviewer, "POST", "/admin/ngo-verifications/" + id + "/decisao",
                objectMapper.writeValueAsString(Map.of("decision", decision, "justification", justification)));
        assertEquals(200, result.statusCode());
    }

    private HttpResponse<String> adminRequest(CookieSession session, String method, String path, String body)
            throws Exception {
        String cookies = java.util.Arrays.stream(session.cookies())
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .collect(java.util.stream.Collectors.joining("; "));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Cookie", cookies)
                .header(session.csrfHeader(), session.csrfToken());
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> newNgoRequest(String cnpj, String email) {
        return Map.ofEntries(
                Map.entry("cnpj", cnpj), Map.entry("causaPrincipal", "EDUCACAO"),
                Map.entry("causasSecundarias", List.of()), Map.entry("ods", List.of(4)),
                Map.entry("nome", "ONG Sintética Verificação"),
                Map.entry("descricaoCurta", "Entidade fictícia de teste de verificação."),
                Map.entry("descricao", "ONG fictícia criada exclusivamente no projeto Supabase TEST."),
                Map.entry("anoFundacao", 2010), Map.entry("email", email),
                Map.entry("areaGeografica", "cidade"), Map.entry("pais", "Brasil"),
                Map.entry("estado", "SP"), Map.entry("cidade", "Cidade Sintética QA"),
                Map.entry("cep", "99999-999"), Map.entry("bairro", "Bairro Fictício"),
                Map.entry("rua", "Rua de Teste"), Map.entry("numero", "1"),
                Map.entry("atendimentoPresencial", false), Map.entry("atendimentoOnline", true),
                Map.entry("necessitaAgendamento", false), Map.entry("aceitaVoluntarios", true),
                Map.entry("processoSeletivoVoluntario", false), Map.entry("emiteCertificadoVoluntario", false));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private long evidenceCount(long appId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM public.ngo_representative_verification_evidence e
                JOIN public.ngo_representative_verification_submissions s ON s.id=e.submission_id
                WHERE s.application_id=?
                """, Long.class, appId);
    }

    private void recordEvidenceLedger(long appId) throws IOException {
        ledgerPut("evidenceIds", List.copyOf(evidenceIds));
        List<String> keys = jdbc.queryForList("""
                SELECT e.storage_key FROM public.ngo_representative_verification_evidence e
                JOIN public.ngo_representative_verification_submissions s ON s.id=e.submission_id
                WHERE s.application_id=? AND e.storage_key IS NOT NULL ORDER BY e.id
                """, String.class, appId);
        retainedObjectKeys.clear(); retainedObjectKeys.addAll(keys);
        ledgerPut("retainedEvidenceObjectKeys", List.copyOf(retainedObjectKeys));
    }

    private void updateLedger(String key, Object value) throws IOException { ledgerPut(key, value); }
    private void ledgerPut(String key, Object value) throws IOException {
        fixtureLedger.put(key, value);
        String location = System.getenv("NGO_VERIFICATION_FIXTURE_LEDGER");
        Path path = location == null || location.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "ngo-verification-supabase-fixtures.json")
                : Path.of(location);
        Files.createDirectories(path.getParent());
        Files.writeString(path, objectMapper.writeValueAsString(fixtureLedger), StandardCharsets.UTF_8);
    }

    private void assertSupabaseTestDatasource() {
        assertEquals("postgres", jdbc.queryForObject("SELECT current_database()", String.class));
        assertEquals("postgres", jdbc.queryForObject("SELECT current_user", String.class));
        String url = System.getenv("DB_URL");
        assertNotNull(url);
        assertTrue(url.matches("^jdbc:postgresql://.*\\.supabase\\.(com|co)(?::[0-9]+)?/postgres.*$"));
    }

    private String syntheticEmail(String role) { return "ngo-verification-" + role + "-" + UUID.randomUUID() + "@example.invalid"; }
    private String syntheticPassword() { return "Synthetic-QA-" + UUID.randomUUID() + "-Aa9!"; }
    private static byte[] pdfBytes() { return "%PDF-1.4\n%Synthetic fictitious verification sample only\n".getBytes(StandardCharsets.US_ASCII); }
    private static byte[] jpegBytes() { return new byte[] {(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xe0,0x00,0x10,0x53,0x59,0x4e,0x54,0x48,0x45,0x54,0x49,0x43}; }
    private static byte[] pngBytes() { return new byte[] {(byte)0x89,'P','N','G',0x0d,0x0a,0x1a,0x0a,0x53,0x59,0x4e,0x54,0x48,0x45,0x54,0x49,0x43}; }

    private String syntheticCpf() { return cpfFromBase(String.format("%09d", RANDOM.nextInt(100_000_000, 999_999_999))); }
    private String syntheticCnpj() {
        String base = String.format("%08d0001", RANDOM.nextInt(10_000_000, 99_999_999));
        int[] firstWeights = {5,4,3,2,9,8,7,6,5,4,3,2};
        int[] secondWeights = {6,5,4,3,2,9,8,7,6,5,4,3,2};
        int first = cnpjDigit(base, firstWeights);
        String withFirst = base + first;
        return withFirst + cnpjDigit(withFirst, secondWeights);
    }
    private static int cnpjDigit(String value, int[] weights) {
        int total=0; for(int i=0;i<weights.length;i++) total+=(value.charAt(i)-'0')*weights[i];
        int rem=total%11; return rem<2?0:11-rem;
    }
    private static String cpfFromBase(String base) {
        int first=cpfDigit(base,10); String withFirst=base+first; int second=cpfDigit(withFirst,11);
        String result=withFirst+second; new Cpf(result); return result;
    }
    private static int cpfDigit(String value,int weight) {
        int sum=0; for(int i=0;i<value.length();i++) sum+=(value.charAt(i)-'0')*(weight-i);
        int rem=(sum*10)%11; return rem==10?0:rem;
    }

    private static HttpServer startExternalMock() {
        try {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/", exchange -> answerSyntheticAddress(exchange));
            server.start();
            return server;
        } catch(IOException failure) { throw new ExceptionInInitializerError(failure); }
    }
    private static void answerSyntheticAddress(HttpExchange exchange) throws IOException {
        String path=exchange.getRequestURI().getPath();
        String response;
        if(path.startsWith("/office/")) {
            String taxId=path.substring("/office/".length());
            response="{\"taxId\":\""+taxId+"\",\"alias\":\"ONG Sintética\",\"founded\":\"2010-01-01\",\"status\":{\"text\":\"Ativa\"},\"company\":{\"name\":\"Entidade Fictícia\",\"nature\":{\"id\":\"3999\",\"text\":\"Associação Privada\"}}}";
        } else if(path.equals("/search")) response="[{\"lat\":\"-23.5505\",\"lon\":\"-46.6333\"}]";
        else { exchange.sendResponseHeaders(404,-1); exchange.close(); return; }
        byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.sendResponseHeaders(200,bytes.length);
        try(var output=exchange.getResponseBody()){output.write(bytes);}
    }

    private record CookieSession(Cookie csrfCookie,String csrfHeader,String csrfToken,
                                 Cookie access,Cookie refresh,Cookie admin) {
        Cookie[] cookies(){return java.util.Arrays.stream(new Cookie[]{csrfCookie,access,refresh,admin})
                .filter(java.util.Objects::nonNull).toArray(Cookie[]::new);}
    }
}
