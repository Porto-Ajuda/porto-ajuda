package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.StripeClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real TEST database, secured submission and scheduled exact-job worker with a provider mock; never calls Gemini. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.ngo-verification.enabled=true",
        "app.ngo-verification.ai-analysis.enabled=true",
        "app.ngo-verification.ai-analysis.results-read-enabled=true",
        "app.ngo-verification.ai-analysis.provider=test-mock",
        "app.ngo-verification.ai-analysis.model=synthetic-analysis-v1",
        "app.ngo-verification.ai-analysis.worker-enabled=false",
        "app.ngo-verification.ai-analysis.evaluation-worker-enabled=true",
        "app.ngo-verification.ai-analysis.evaluation-poll-ms=50",
        "app.ngo-verification.ai-analysis.evaluation-max-provider-requests=3",
        "app.ngo-verification.ai-analysis.timeout=10s",
        "app.ngo-verification.auto-approval.enabled=false",
        "app.initialization.seed-roles=false",
        "app.maintenance.cleanup-cron=-",
        "app.admin-auth.controls-cleanup-cron=-",
        "app.admin-auth.delivery-worker.enabled=false",
        "app.cookies.secure=false",
        "ALLOWED_ORIGINS=http://localhost:8080",
        "APP_FRONTEND_URL=http://localhost:8080",
        "APP_BACKEND_URL=http://localhost:8080"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(NgoVerificationSupabaseTargetCondition.class)
class NgoVerificationMockedWorkerSupabaseIntegrationTest {
    private static final String NGO_NAME = "ASSOCIACAO FICTICIA WORKER REGRESSION";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(1);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpServer EXTERNAL_MOCK = startExternalMock();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SessaoUsuarioService userSessions;
    @Autowired VerificacaoAnaliseEvaluationWorker evaluationWorker;
    @MockitoBean AnaliseRepresentacaoDocumental provider;
    @MockitoBean EmailService emailService;
    @MockitoBean SmsService smsService;
    @MockitoBean StripeClient stripeClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Long> jobEventIds = new ArrayList<>();
    private final java.util.Map<String, Object> ledger = new java.util.LinkedHashMap<>();
    private Path ledgerPath;
    private Integer userId;
    private String email;
    private Integer oscId;
    private Long applicationId;
    private Long submissionId;
    private Long evidenceId;

    @DynamicPropertySource
    static void externalProviderCallsAreLocalMocks(DynamicPropertyRegistry properties) {
        String base = "http://127.0.0.1:" + EXTERNAL_MOCK.getAddress().getPort();
        properties.add("cnpja.url-base", () -> base + "/office/");
        properties.add("app.geocoding.base-url", () -> base);
    }

    @AfterAll
    static void stopAddressMocks() { EXTERNAL_MOCK.stop(0); }

    @Test
    void securedSubmissionDuplicateAndRepeatedPollingUseOneMockedProviderCall() throws Exception {
        assertEquals("postgres", jdbc.queryForObject("SELECT current_database()", String.class));
        String runId = UUID.randomUUID().toString();
        ledger.put("runId", runId);
        ledger.put("purpose", "secured submission with a mocked analysis adapter; no Gemini request");
        writeLedger();
        email = "ngo-ai-mocked-worker-" + runId + "@example.invalid";
        String password = "Synthetic-Mock-" + runId + "-R8!";
        userId = createSyntheticUser(email, password);
        ledger.put("applicantUserId", userId);
        writeLedger();
        CookieSession applicant = login(email, password);
        oscId = registerSyntheticOrganization(applicant, syntheticCnpj());
        ledger.put("oscId", oscId);
        writeLedger();
        JsonNode application = json(mockMvc.perform(get("/osc/" + oscId + "/verificacao")
                .cookie(applicant.cookies())).andReturn(), 200);
        applicationId = application.path("applicationId").asLong();
        ledger.put("applicationId", applicationId);
        writeLedger();
        assertTrue(applicationId > 0);
        assertEquals("PENDING", application.path("status").asText());

        MvcResult uploaded = mockMvc.perform(multipart("/osc/" + oscId + "/verificacao/documentos")
                .file(new MockMultipartFile("arquivo", "synthetic-no-authority.pdf", "application/pdf", syntheticPdf()))
                .param("tipo", "REPRESENTATION_PROOF")
                .cookie(applicant.cookies()).header(applicant.csrfHeader(), applicant.csrfToken())).andReturn();
        assertEquals(201, uploaded.getResponse().getStatus());
        evidenceId = mapper.readTree(uploaded.getResponse().getContentAsByteArray())
                .path("evidence").get(0).path("id").asLong();
        ledger.put("evidenceId", evidenceId);
        writeLedger();

        var reference = List.of(new AnaliseRepresentacaoDocumental.ReferenciaPagina(1, 1));
        when(provider.analisar(any())).thenReturn(new AnaliseRepresentacaoDocumental.Resultado(
                Instant.now(), AnaliseRepresentacaoDocumental.Conclusao.INCONCLUSIVO,
                List.of(
                        new AnaliseRepresentacaoDocumental.CampoExtraido(AnaliseRepresentacaoDocumental.Campo.NOME_ENTIDADE,
                                AnaliseRepresentacaoDocumental.EstadoCampo.ENCONTRADO, NGO_NAME, reference),
                        new AnaliseRepresentacaoDocumental.CampoExtraido(AnaliseRepresentacaoDocumental.Campo.CNPJ,
                                AnaliseRepresentacaoDocumental.EstadoCampo.ENCONTRADO, syntheticCurrentCnpj(), reference),
                        new AnaliseRepresentacaoDocumental.CampoExtraido(AnaliseRepresentacaoDocumental.Campo.REPRESENTANTE_LEGAL,
                                AnaliseRepresentacaoDocumental.EstadoCampo.AUSENTE, null, reference),
                        new AnaliseRepresentacaoDocumental.CampoExtraido(AnaliseRepresentacaoDocumental.Campo.PODERES_REPRESENTACAO,
                                AnaliseRepresentacaoDocumental.EstadoCampo.INCERTO, null, reference)),
                List.of(), "test-mock", "synthetic-analysis-v1", 32, 16));

        JsonNode submitted = json(mockMvc.perform(post("/osc/" + oscId + "/verificacao/enviar")
                .cookie(applicant.cookies()).header(applicant.csrfHeader(), applicant.csrfToken())).andReturn(), 200);
        assertEquals("PENDING", submitted.path("status").asText());
        submissionId = jdbc.queryForObject("""
                SELECT id FROM public.ngo_representative_verification_submissions
                 WHERE application_id=? AND version_number=1 AND submitted_at IS NOT NULL
                """, Long.class, applicationId);
        ledger.put("submissionId", submissionId);
        writeLedger();
        long jobId = jdbc.queryForObject("""
                SELECT id FROM public.ngo_representative_verification_ai_jobs
                 WHERE application_id=? AND submission_id=?
                """, Long.class, applicationId, submissionId);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                Integer.class, applicationId));

        assertTrue(awaitCompleted(jobId), "The scheduled scoped worker must process this normal secured submission.");
        assertEquals(1, evaluationWorker.providerCallsUsed());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                Integer.class, jobId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_job_events WHERE job_id=? AND event_type='COMPLETED'",
                Integer.class, jobId));

        MvcResult duplicate = mockMvc.perform(post("/osc/" + oscId + "/verificacao/enviar")
                .cookie(applicant.cookies()).header(applicant.csrfHeader(), applicant.csrfToken())).andReturn();
        assertEquals(409, duplicate.getResponse().getStatus());
        for (int i = 0; i < 5; i++) {
            assertEquals(200, mockMvc.perform(get("/osc/" + oscId + "/verificacao")
                    .cookie(applicant.cookies())).andReturn().getResponse().getStatus());
            evaluationWorker.pollRegisteredJobs();
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE application_id=?",
                Integer.class, applicationId));
        assertEquals(1, evaluationWorker.providerCallsUsed());
        verify(provider, times(1)).analisar(any());
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM public.ngo_representative_verification_applications WHERE id=?",
                String.class, applicationId));

        jobEventIds.addAll(jdbc.query("SELECT id FROM public.ngo_representative_verification_ai_job_events WHERE job_id=? ORDER BY id",
                (rs, row) -> rs.getLong(1), jobId));
        ledger.put("fixtureType", "mock-provider automatic worker lifecycle; synthetic applicant and NGO");
        ledger.put("applicantUserId", userId);
        ledger.put("oscId", oscId);
        ledger.put("applicationId", applicationId);
        ledger.put("submissionId", submissionId);
        ledger.put("evidenceId", evidenceId);
        ledger.put("jobId", jobId);
        ledger.put("providerCalls", 1);
        ledger.put("jobStatus", "COMPLETED");
        ledger.put("applicationStatus", "PENDING");
        ledger.put("jobEvents", jobEventIds);
        writeLedger();
    }

    @AfterEach
    void deactivateOnlySyntheticApplicant() throws Exception {
        if (userId != null) {
            userSessions.revogarSessoesDoUsuario(userId);
            jdbc.update("UPDATE public.usuarios SET ativo=FALSE WHERE id=?", userId);
            ledger.put("applicantAccountAfterCleanup", "INACTIVE");
        }
        if (ledgerPath != null) writeLedger();
    }

    private int createSyntheticUser(String syntheticEmail, String rawPassword) {
        Integer id = jdbc.queryForObject("""
                INSERT INTO public.usuarios(cpf,data_nascimento,nome,nome_social,genero,email,telefone,senha_hash,ativo,
                                            email_verificado,telefone_verificado,data_criacao)
                VALUES(?,?,?,?, 'PREFIRO_NAO_INFORMAR', ?,NULL,?,TRUE,TRUE,FALSE,CURRENT_TIMESTAMP) RETURNING id
                """, Integer.class, syntheticCpf(), LocalDate.of(1990, 1, 1),
                "Conta Sintetica Worker Mock", null, syntheticEmail, passwordEncoder.encode(rawPassword));
        Integer roleId = jdbc.queryForObject("SELECT id FROM public.roles WHERE nome='USUARIO'", Integer.class);
        jdbc.update("INSERT INTO public.usuario_roles(id_usuario,id_role) VALUES(?,?)", id, roleId);
        return id;
    }

    private int registerSyntheticOrganization(CookieSession session, String cnpj) throws Exception {
        Map<String, Object> body = Map.ofEntries(
                Map.entry("cnpj", cnpj), Map.entry("causaPrincipal", "EDUCACAO"),
                Map.entry("causasSecundarias", List.of()), Map.entry("ods", List.of(4)),
                Map.entry("nome", NGO_NAME), Map.entry("descricaoCurta", "Synthetic evaluation target."),
                Map.entry("descricao", "Fictitious NGO for mocked worker regression."), Map.entry("anoFundacao", 2010),
                Map.entry("email", "ngo-worker-" + UUID.randomUUID() + "@example.invalid"),
                Map.entry("areaGeografica", "cidade"), Map.entry("pais", "Brasil"), Map.entry("estado", "SP"),
                Map.entry("cidade", "Cidade Sintetica"), Map.entry("cep", "00000-000"),
                Map.entry("bairro", "Bairro Sintetico"), Map.entry("rua", "Rua Ficticia"), Map.entry("numero", "1"),
                Map.entry("atendimentoPresencial", false), Map.entry("atendimentoOnline", true),
                Map.entry("necessitaAgendamento", false), Map.entry("aceitaVoluntarios", true),
                Map.entry("processoSeletivoVoluntario", false), Map.entry("emiteCertificadoVoluntario", false));
        MvcResult result = mockMvc.perform(post("/osc/create").queryParam("representacao", "LEGAL_REPRESENTATIVE")
                .cookie(session.cookies()).header(session.csrfHeader(), session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andReturn();
        return json(result, 201).path("id").asInt();
    }

    private CookieSession login(String loginEmail, String rawPassword) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/usuario/csrf")).andReturn();
        JsonNode csrf = mapper.readTree(csrfResult.getResponse().getContentAsByteArray());
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        MvcResult login = mockMvc.perform(post("/usuario/login")
                .with(request -> {
                    int address = IP_SEQUENCE.getAndIncrement();
                    request.setRemoteAddr("198.18." + ((address / 254) % 2) + "." + ((address % 254) + 1));
                    return request;
                })
                .cookie(csrfCookie).header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("email", loginEmail, "senha", rawPassword, "lembrarDeMim", false))))
                .andReturn();
        assertEquals(200, login.getResponse().getStatus());
        Cookie access = login.getResponse().getCookie("PA_ACCESS");
        Cookie refresh = login.getResponse().getCookie("PA_REFRESH");
        assertNotNull(access);
        return new CookieSession(csrfCookie, csrf.path("headerName").asText(), csrf.path("token").asText(), access, refresh);
    }

    private JsonNode json(MvcResult result, int expected) throws Exception {
        assertEquals(expected, result.getResponse().getStatus());
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private boolean awaitCompleted(long jobId) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            String status = jdbc.queryForObject("SELECT status FROM public.ngo_representative_verification_ai_jobs WHERE id=?",
                    String.class, jobId);
            if ("COMPLETED".equals(status)) return true;
            if ("FAILED".equals(status) || "SUPERSEDED".equals(status)) return false;
            Thread.sleep(50);
        }
        return false;
    }

    private String syntheticCurrentCnpj() {
        return jdbc.queryForObject("SELECT cnpj FROM public.oscs WHERE id=?", String.class, oscId);
    }

    private byte[] syntheticPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0))) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.newLineAtOffset(40, 740);
                content.showText("SYNTHETIC TEST ONLY - NO REAL PERSON - NO REPRESENTATION AUTHORITY");
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private String syntheticCpf() {
        String base = String.format("%09d", RANDOM.nextInt(100_000_000, 999_999_999));
        int first = cpfDigit(base, 10);
        String partial = base + first;
        return partial + cpfDigit(partial, 11);
    }

    private int cpfDigit(String value, int weight) {
        int sum = 0;
        for (int i = 0; i < value.length(); i++) sum += (value.charAt(i) - '0') * (weight - i);
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }

    private String syntheticCnpj() {
        String base = String.format("%08d0001", RANDOM.nextInt(10_000_000, 99_999_999));
        int[] firstWeights = {5,4,3,2,9,8,7,6,5,4,3,2};
        int[] secondWeights = {6,5,4,3,2,9,8,7,6,5,4,3,2};
        String partial = base + cnpjDigit(base, firstWeights);
        return partial + cnpjDigit(partial, secondWeights);
    }

    private int cnpjDigit(String value, int[] weights) {
        int total = 0;
        for (int i = 0; i < weights.length; i++) total += (value.charAt(i) - '0') * weights[i];
        int remainder = total % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private void writeLedger() throws IOException {
        String configured = System.getenv("NGO_VERIFICATION_FIXTURE_LEDGER");
        ledgerPath = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "ngo-ai-mocked-worker-ledger-" + UUID.randomUUID() + ".json")
                : Path.of(configured);
        Files.createDirectories(ledgerPath.getParent());
        Files.writeString(ledgerPath, mapper.writeValueAsString(ledger), StandardCharsets.UTF_8);
    }

    private static HttpServer startExternalMock() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", NgoVerificationMockedWorkerSupabaseIntegrationTest::respondExternalMock);
            server.start();
            return server;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void respondExternalMock(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body;
        if (path.startsWith("/office/")) {
            String cnpj = path.substring("/office/".length());
            body = "{\"taxId\":\"" + cnpj + "\",\"alias\":\"Entidade Sintetica\","
                    + "\"founded\":\"2010-01-01\",\"status\":{\"text\":\"Ativa\"},"
                    + "\"company\":{\"name\":\"" + NGO_NAME + "\",\"nature\":{\"id\":\"3999\",\"text\":\"Associacao Privada\"}}}";
        } else if (path.equals("/search")) {
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

    private record CookieSession(Cookie csrfCookie, String csrfHeader, String csrfToken, Cookie access, Cookie refresh) {
        Cookie[] cookies() { return new Cookie[]{csrfCookie, access, refresh}; }
    }
}
