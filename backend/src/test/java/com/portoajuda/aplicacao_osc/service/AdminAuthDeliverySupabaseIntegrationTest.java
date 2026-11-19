package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;

import com.portoajuda.aplicacao_osc.dto.request.AdminAuthStartRequest;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Opt-in real-PostgreSQL outbox integration. Providers and the scheduled worker are test doubles. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.admin.rbac.enforcement-enabled=true",
        "app.initialization.seed-roles=false",
        "app.admin-auth.controls-cleanup-cron=-",
        "app.maintenance.cleanup-cron=-"
})
@EnabledIfEnvironmentVariable(named = "ADMIN_AUTH_DELIVERY_TEST_DATABASE_CONFIRMED", matches = "true")
class AdminAuthDeliverySupabaseIntegrationTest {

    private static final String TEST_PASSWORD_PREFIX = "Synthetic-Admin-MFA-";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private UsuarioRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AdminAuthCodeSecurity crypto;
    @Autowired private AdminAuthenticationService authentication;
    @Autowired private AdminAuthDeliveryQueueService queue;
    @Autowired private AdminAuthChallengeRepository challenges;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private EmailService mockedEmailService;
    @MockitoBean private SmsService mockedSmsService;
    @MockitoBean private AdminAuthDeliveryWorker mockedScheduledWorker;

    private Usuario fixture;
    private String email;
    private String password;
    private String testIp;
    private UUID assignmentId;
    private final List<UUID> challengeIds = new ArrayList<>();

    @BeforeEach
    void createSyntheticAdminAndVerifyConfiguredPostgresTarget() throws Exception {
        String jdbcUrl;
        String sslMode;
        try (Connection connection = dataSource.getConnection()) {
            jdbcUrl = connection.getMetaData().getURL();
        }
        sslMode = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class)
                .getDataSourceProperties().getProperty("sslmode");
        assumeTrue(jdbcUrl.startsWith("jdbc:postgresql://"), "Requires PostgreSQL.");
        assumeTrue(jdbcUrl.toLowerCase().contains("supabase"),
                "Explicit test opt-in must resolve to the configured Supabase TEST datasource.");
        assumeTrue(jdbcUrl.matches("(?i).*/postgres(?:[?].*)?$"),
                "Expected the configured Supabase TEST database name.");
        assertEquals("require", sslMode, "TLS must be required for this test connection.");

        String suffix = UUID.randomUUID().toString();
        email = "synthetic-admin-delivery-" + suffix + "@example.invalid";
        password = TEST_PASSWORD_PREFIX + suffix;
        testIp = "203.0.113." + (1 + new SecureRandom().nextInt(254));

        Role adminRole = roles.findByNome("ADMIN").orElseThrow(
                () -> new AssertionError("Expected the existing ADMIN role; the test never seeds roles."));
        fixture = new Usuario();
        fixture.setNome("Synthetic MFA delivery fixture");
        fixture.setEmail(new Email(email));
        fixture.setCpf(new Cpf(uniqueValidSyntheticCpf()));
        fixture.setDataNascimento(LocalDate.of(1990, 1, 1));
        fixture.setGenero(com.portoajuda.aplicacao_osc.enums.Genero.PREFIRO_NAO_INFORMAR);
        fixture.setTelefone("11987654321");
        fixture.setSenha(passwordEncoder.encode(password));
        fixture.setEmailVerificado(true);
        fixture.setTelefoneVerificado(true);
        fixture.setAtivo(true);
        fixture.getRoles().add(adminRole);
        fixture = users.saveAndFlush(fixture);
        assertTrue(fixture.getId() != 47 && fixture.getId() != 48,
                "Synthetic fixture must not be either protected administrator account.");

        assignmentId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO public.admin_assignments (
                    id, usuario_id, usuario_id_snapshot, profile, status, granted_at,
                    granted_by_user_id_snapshot, last_modified_at,
                    last_modified_by_user_id_snapshot, revoked_at, reason, version)
                VALUES (?, ?, ?, 'PLATFORM_ADMIN', 'ACTIVE', ?, NULL, ?, NULL, NULL, ?, 0)
                """, assignmentId, fixture.getId(), fixture.getId(), Timestamp.from(now),
                Timestamp.from(now), "Synthetic MFA delivery integration fixture");
    }

    @AfterEach
    void cleanOnlySyntheticFixturesAndRetainAppendOnlyAuditRows() throws Exception {
        if (fixture == null || fixture.getId() == null) return;
        Integer userId = fixture.getId();

        for (UUID challengeId : challengeIds) {
            jdbc.update("DELETE FROM public.admin_auth_deliveries WHERE challenge_id = ?", challengeId);
            jdbc.update("DELETE FROM public.admin_auth_challenges WHERE id = ?", challengeId);
        }
        jdbc.update("DELETE FROM public.admin_assignments WHERE id = ? AND usuario_id = ?",
                assignmentId, userId);
        jdbc.update("DELETE FROM public.usuario_roles WHERE id_usuario = ?", userId);
        jdbc.update("DELETE FROM public.usuarios WHERE id = ?", userId);
        cleanupThrottle("START_ACCOUNT", crypto.accountKey(email));
        cleanupThrottle("START_IP", testIp);
        // admin_security_audit_events is append-only; synthetic audit rows remain by design.
        writeFixtureLedger(userId);
        fixture = null;
    }

    @Test
    void committedSyntheticChallengeIsClaimedAndBothMockProvidersCompleteTheOutbox() {
        UUID[] challengeRef = new UUID[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            challengeRef[0] = startChallenge();
            UUID challengeId = challengeRef[0];
            entityManager.flush();
            assertEquals("ISSUING", scalar("SELECT state FROM public.admin_auth_challenges WHERE id = ?", challengeId));
            assertEquals(2, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ?", challengeId));
            assertEquals(2, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'PENDING'",
                    challengeId));
            // Commit the real initiation/outbox rows while keeping them invisible to any other
            // worker's claim predicate. The next transaction restores ISSUING only in its own
            // uncommitted view and holds the queue table lock while our mocked worker processes.
            jdbc.update("UPDATE public.admin_auth_challenges SET state = 'PENDING' WHERE id = ?", challengeId);
        });

        UUID challengeId = challengeRef[0];
        assertEquals("PENDING", scalar("SELECT state FROM public.admin_auth_challenges WHERE id = ?", challengeId));
        assertEquals(2, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'PENDING'",
                challengeId), "Initiated challenge and outbox must be committed before worker claim.");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL lock_timeout = '5s'");
            jdbc.execute("LOCK TABLE public.admin_auth_deliveries IN EXCLUSIVE MODE");
            jdbc.update("UPDATE public.admin_auth_challenges SET state = 'ISSUING' WHERE id = ?", challengeId);

            manualWorker().processNextDelivery();
            manualWorker().processNextDelivery();
            entityManager.flush();

            long sent = number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'SENT'",
                    challengeId);
            long pending = number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'PENDING'",
                    challengeId);
            long failed = number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'FAILED'",
                    challengeId);
            List<String> deliveryEvents = jdbc.queryForList("""
                    SELECT event_type FROM public.admin_security_audit_events
                     WHERE challenge_id = ? AND event_type LIKE 'ADMIN_AUTH_DELIVERY%'
                     ORDER BY occurred_at
                    """, String.class, challengeId);
            assertEquals(2, sent, "Mock-provider flow states: sent=" + sent + ", pending=" + pending
                    + ", failed=" + failed + ", sanitizedDeliveryEvents=" + deliveryEvents);
            assertEquals("PENDING", scalar("SELECT state FROM public.admin_auth_challenges WHERE id = ?", challengeId));
            assertEquals(2, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'SENT'",
                    challengeId));
            assertEquals(2, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND attempt_count = 1",
                    challengeId));
        });

        // Verify the completed state from a fresh autocommit transaction.
        assertEquals("PENDING", scalar("SELECT state FROM public.admin_auth_challenges WHERE id = ?", challengeId));
        assertEquals(2, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'SENT'",
                challengeId));

        verify(mockedEmailService, times(1)).enviarCodigoAutenticacaoAdministrativa(eq(email), anyString());
        verify(mockedSmsService, times(1)).enviarSms(eq("5511987654321"), anyString(), eq(challengeId + ":sms"));
    }

    @Test
    void bulkJpaInvalidationLeavesCachedEntityStaleButFreshQueueTransactionRejectsIt() {
        UUID[] challengeRef = new UUID[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL lock_timeout = '5s'");
            jdbc.execute("LOCK TABLE public.admin_auth_deliveries IN EXCLUSIVE MODE");
            challengeRef[0] = startChallenge();
            entityManager.flush();
            UUID challengeId = challengeRef[0];
            AdminAuthChallenge managed = challenges.findById(challengeId).orElseThrow();
            assertEquals(AdminAuthChallenge.State.ISSUING, managed.getState());
            assertEquals(1, challenges.invalidatePendingForUser(
                    fixture.getId(), Instant.now(), AdminAuthChallenge.State.INVALIDATED,
                    List.of(AdminAuthChallenge.State.ISSUING, AdminAuthChallenge.State.PENDING)));
            // JPQL bulk UPDATE does not refresh entities already in this persistence context.
            assertEquals(AdminAuthChallenge.State.ISSUING, managed.getState());
            queue.clearInvalidatedForUser(fixture.getId());
        });
        UUID challengeId = challengeRef[0];
        manualWorker().processNextDelivery();
        manualWorker().processNextDelivery();

        assertEquals("INVALIDATED", scalar("SELECT state FROM public.admin_auth_challenges WHERE id = ?", challengeId));
        assertEquals(2, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'FAILED'",
                challengeId));
        verify(mockedEmailService, never()).enviarCodigoAutenticacaoAdministrativa(anyString(), anyString());
        verify(mockedSmsService, never()).enviarSms(anyString(), anyString(), anyString());
    }

    @Test
    void concurrentWorkersSerializeRequiredChannelsForOneChallenge() throws Exception {
        UUID challengeId = startChallenge();
        CountDownLatch providersStarted = new CountDownLatch(1);
        CountDownLatch releaseProviders = new CountDownLatch(1);
        doAnswer(invocation -> {
            providersStarted.countDown();
            assertTrue(releaseProviders.await(60, TimeUnit.SECONDS));
            return null;
        }).when(mockedEmailService).enviarCodigoAutenticacaoAdministrativa(eq(email), anyString());
        doAnswer(invocation -> {
            providersStarted.countDown();
            assertTrue(releaseProviders.await(60, TimeUnit.SECONDS));
            return new SmsMaisSendResult("synthetic-provider-id", "synthetic-external-id");
        }).when(mockedSmsService).enviarSms(eq("5511987654321"), anyString(), eq(challengeId + ":sms"));

        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<?> first = workers.submit(() -> manualWorker().processNextDelivery());
        Future<?> second = workers.submit(() -> manualWorker().processNextDelivery());
        try {
            assertTrue(providersStarted.await(10, TimeUnit.SECONDS),
                    "One worker must claim a channel while the sibling waits behind the same challenge.");
            releaseProviders.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            releaseProviders.countDown();
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(1, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'SENT'",
                challengeId));
        assertEquals(1, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'PENDING'",
                challengeId));
        assertEquals(0, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'FAILED'",
                challengeId));

        manualWorker().processNextDelivery();

        assertEquals("PENDING", scalar("SELECT state FROM public.admin_auth_challenges WHERE id = ?", challengeId));
        assertEquals(2, number("SELECT count(*) FROM public.admin_auth_deliveries WHERE challenge_id = ? AND state = 'SENT'",
                challengeId));
        verify(mockedEmailService, times(1)).enviarCodigoAutenticacaoAdministrativa(eq(email), anyString());
        verify(mockedSmsService, times(1)).enviarSms(eq("5511987654321"), anyString(), eq(challengeId + ":sms"));
    }

    private UUID startChallenge() {
        UUID challengeId = authentication.iniciar(new AdminAuthStartRequest(email, password), testIp);
        challengeIds.add(challengeId);
        assertEquals(1, number("SELECT count(*) FROM public.admin_auth_challenges WHERE id = ?", challengeId),
                "The real authentication service must commit the synthetic challenge before the worker sees it.");
        return challengeId;
    }

    private AdminAuthDeliveryWorker manualWorker() {
        return new AdminAuthDeliveryWorker(queue, mockedEmailService, mockedSmsService);
    }

    private void cleanupThrottle(String type, String subject) {
        String key = crypto.hashIdentifier("control:" + type, subject);
        jdbc.update("DELETE FROM public.admin_auth_controls WHERE control_key = ?", key);
    }

    private long number(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String scalar(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private void writeFixtureLedger(Integer userId) throws Exception {
        Path ledger = Path.of(System.getProperty("java.io.tmpdir"),
                "porto-ajuda-admin-auth-delivery-fixtures.tsv");
        String challenges = challengeIds.stream().map(UUID::toString)
                .collect(java.util.stream.Collectors.joining(","));
        String line = "userId=" + userId + "\tassignmentId=" + assignmentId
                + "\tchallengeIds=" + challenges + "\tbusinessRowsCleaned=true"
                + "\tappendOnlyAuditRetained=true" + System.lineSeparator();
        Files.writeString(ledger, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String uniqueValidSyntheticCpf() {
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder base = new StringBuilder(9);
            for (int i = 0; i < 9; i++) base.append(random.nextInt(10));
            String cpf = appendCpfDigit(base.toString());
            if (jdbc.queryForObject("SELECT NOT EXISTS(SELECT 1 FROM public.usuarios WHERE cpf = ?)",
                    Boolean.class, cpf)) return cpf;
        }
        throw new IllegalStateException("Unable to allocate a unique synthetic CPF fixture.");
    }

    private String appendCpfDigit(String base) {
        String first = base + cpfDigit(base, 10);
        return first + cpfDigit(first, 11);
    }

    private int cpfDigit(String value, int weightStart) {
        int sum = 0;
        for (int i = 0; i < value.length(); i++) sum += (value.charAt(i) - '0') * (weightStart - i);
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
