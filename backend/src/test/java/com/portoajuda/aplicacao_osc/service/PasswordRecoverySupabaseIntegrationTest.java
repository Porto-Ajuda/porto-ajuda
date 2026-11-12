package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.CodigoAutenticacao;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.AdminPrivilegedSessionRepository;
import com.portoajuda.aplicacao_osc.repository.CodigoAutenticacaoRepository;
import com.portoajuda.aplicacao_osc.repository.RecuperacaoSenhaRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.SessaoUsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.service.PasswordRecoveryRequested;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.util.UriUtils;

/**
 * Opt-in real PostgreSQL validation. It is intentionally disabled unless the caller
 * explicitly marks the configured Supabase database as the disposable TEST database.
 * It creates a single synthetic account per test and removes only rows linked to that ID.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "spring.jpa.hibernate.ddl-auto=validate"})
@EnabledIfEnvironmentVariable(named = "PASSWORD_RECOVERY_TEST_DATABASE_CONFIRMED", matches = "true")
class PasswordRecoverySupabaseIntegrationTest {
    private static final String OLD_PASSWORD = "OldPassw0rd!";
    private static final String RESET_PASSWORD = "ResetPassw0rd!";
    private static final String CHANGED_PASSWORD = "ChangedPassw0rd!";

    private final java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired UsuarioRepository users;
    @Autowired com.portoajuda.aplicacao_osc.segurity.JwtService jwtService;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired SessaoUsuarioRepository sessions;
    @Autowired RecuperacaoSenhaRepository recoveries;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired RecuperacaoSenhaService recoveryService;
    @Autowired UsuarioService userService;
    @Autowired SessaoUsuarioService userSessionService;
    @Autowired AdminPrivilegedSessionService adminSessionService;
    @Autowired AdminAuthChallengeRepository challenges;
    @Autowired AdminPrivilegedSessionRepository adminSessions;
    @Autowired CodigoAutenticacaoRepository loginCodes;
    @MockitoSpyBean AdminPrivilegedSessionService adminSessionSpy;
    @LocalServerPort int localPort;
    @MockitoBean EmailService emailService;
    @MockitoBean AdminAuthDeliveryWorker adminDeliveryWorker;
    @MockitoBean SmsService smsService;

    @Value("${app.password-recovery.pepper}") String pepper;
    @Value("${app.frontend-url}") String frontendUrl;
    private Usuario fixture;
    private String email;
    private String testIp;

    @BeforeEach
    void createSyntheticAccountAndAssertDatabaseTarget() throws Exception {
        String jdbcUrl;
        String sslmode;
        try (var connection = dataSource.getConnection()) {
            jdbcUrl = connection.getMetaData().getURL();
        }
        sslmode = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class)
                .getDataSourceProperties().getProperty("sslmode");
        assertTrue(jdbcUrl.startsWith("jdbc:postgresql://"), "The test requires PostgreSQL.");
        assertTrue(jdbcUrl.toLowerCase().contains("supabase"), "The opt-in test target must be the confirmed Supabase TEST database.");
        assertTrue(jdbcUrl.matches("(?i).*/postgres(?:[?].*)?$"), "The approved test database name must be postgres.");
        assertEquals("require", sslmode, "The test connection must require TLS.");

        email = "pw-recovery-it-" + UUID.randomUUID() + "@example.invalid";
        testIp = "198.51.100." + (1 + new SecureRandom().nextInt(254));
        Role userRole = roles.findByNome("USUARIO").orElseThrow(
                () -> new AssertionError("Expected existing USUARIO role; the test does not seed application roles."));
        fixture = new Usuario();
        fixture.setNome("Conta sintética de teste de recuperação");
        fixture.setEmail(new Email(email));
        fixture.setCpf(new Cpf(validSyntheticCpf()));
        fixture.setDataNascimento(LocalDate.of(1990, 1, 1));
        fixture.setGenero(com.portoajuda.aplicacao_osc.enums.Genero.PREFIRO_NAO_INFORMAR);
        fixture.setSenha(encoder.encode(OLD_PASSWORD));
        fixture.setEmailVerificado(true);
        fixture.setTelefoneVerificado(false);
        fixture.setAtivo(true);
        fixture.getRoles().add(userRole);
        fixture = users.saveAndFlush(fixture);
        assertNotEquals(47, fixture.getId());
        assertNotEquals(48, fixture.getId());
        recordSyntheticFixtureId(fixture.getId());
    }

    @AfterEach
    void cleanOnlySyntheticFixtureRows() {
        reset(adminSessionSpy);
        if (fixture == null || fixture.getId() == null) return;
        Integer id = fixture.getId();
        String emailKey = hmac(email);
        String ipKey = hmac(testIp);
        jdbc.update("delete from admin_privileged_sessions where usuario_id = ?", id);
        jdbc.update("delete from admin_auth_deliveries where challenge_id in (select id from admin_auth_challenges where usuario_id = ?)", id);
        jdbc.update("delete from admin_auth_challenges where usuario_id = ?", id);
        jdbc.update("delete from phone_verification_challenges where usuario_id = ?", id);
        jdbc.update("delete from codigos_autenticacao where usuario_id = ?", id);
        jdbc.update("delete from sessoes_usuario where usuario_id = ?", id);
        jdbc.update("delete from recuperacoes_senha where usuario_id = ? or email_key = ? or ip_key = ?", id, emailKey, ipKey);
        jdbc.update("delete from usuario_roles where id_usuario = ?", id);
        jdbc.update("delete from usuarios where id = ?", id);
        fixture = null;
    }

    @Test
    void realHttpRecoveryAndPasswordChangeEnforceCsrfAndInvalidateSessions() throws Exception {
        fixture.getRoles().add(roles.findByNome("ADMIN").orElseThrow());
        fixture.setTelefone("+5511999999999");
        fixture.setTelefoneVerificado(true);
        fixture = users.saveAndFlush(fixture);
        HttpResult sessionBeforeRecovery = request("POST", "/usuario/login", loginBody(OLD_PASSWORD), true, true);
        assertEquals(200, sessionBeforeRecovery.statusCode());
        String accessBeforeRecovery = cookie(sessionBeforeRecovery, "PA_ACCESS");
        String refreshBeforeRecovery = cookie(sessionBeforeRecovery, "PA_REFRESH");
        CodigoAutenticacao loginCode = loginCodes.saveAndFlush(new CodigoAutenticacao(
                "a".repeat(64), fixture, java.time.Instant.now().plusSeconds(300)));
        AdminAuthChallenge pendingChallenge = challenges.saveAndFlush(challenge(AdminAuthChallenge.State.PENDING));
        AdminAuthChallenge verifiedChallenge = challenge(AdminAuthChallenge.State.VERIFIED);
        Instant completedAt = Instant.now();
        verifiedChallenge.setEmailVerifiedAt(completedAt);
        verifiedChallenge.setSmsVerifiedAt(completedAt);
        verifiedChallenge.setCompletedAt(completedAt);
        challenges.saveAndFlush(verifiedChallenge);
        var adminCredentialBeforeRecovery = adminSessionService.emitir(verifiedChallenge.getId(), testIp);
        assertEquals(403, request("POST", "/usuario/recuperacao-senha/solicitar",
                java.util.Map.of("email", email), false, true).statusCode());
        assertEquals(202, request("POST", "/usuario/recuperacao-senha/solicitar",
                java.util.Map.of("email", email), true, true).statusCode());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(10_000)).enviarRecuperacaoSenha(eq(email), link.capture());
        assertTrue(link.getValue().startsWith(frontendUrl + "/login#redefinir-senha/"));
        String marker = "#redefinir-senha/";
        String rawToken = UriUtils.decode(link.getValue().substring(link.getValue().indexOf(marker) + marker.length()), StandardCharsets.UTF_8);
        assertFalse(rawToken.isBlank());

        var resetBody = java.util.Map.of("token", rawToken,
                "novaSenha", RESET_PASSWORD, "confirmarSenha", RESET_PASSWORD);
        HttpResult resetResult = request("POST", "/usuario/recuperacao-senha/redefinir", resetBody, true, true,
                "PA_ACCESS=" + accessBeforeRecovery, "PA_REFRESH=" + refreshBeforeRecovery);
        assertEquals(200, resetResult.statusCode());
        assertTrue(resetResult.cookies().stream().anyMatch(value -> value.startsWith("PA_ACCESS=") && value.contains("Max-Age=0")));
        assertTrue(resetResult.cookies().stream().anyMatch(value -> value.startsWith("PA_REFRESH=") && value.contains("Max-Age=0")));
        assertTrue(loginCodes.findById(loginCode.getId()).isEmpty());
        assertEquals(AdminAuthChallenge.State.INVALIDATED,
                challenges.findById(pendingChallenge.getId()).orElseThrow().getState());
        assertEquals(AdminAuthChallenge.State.INVALIDATED,
                challenges.findById(verifiedChallenge.getId()).orElseThrow().getState());
        assertTrue(adminSessionService.autenticar(
                adminCredentialBeforeRecovery.rawCredential(), testIp).isEmpty());
        assertEquals(401, request("GET", "/usuario/sessao-atual", null, false, true,
                "PA_ACCESS=" + accessBeforeRecovery).statusCode());
        assertEquals(401, request("POST", "/usuario/refresh", null, true, true,
                "PA_REFRESH=" + refreshBeforeRecovery).statusCode());
        assertEquals(400, request("POST", "/usuario/recuperacao-senha/redefinir", resetBody, true, true).statusCode());
        assertEquals(401, request("POST", "/usuario/login", loginBody(OLD_PASSWORD), true, true).statusCode());

        HttpResult login = request("POST", "/usuario/login", loginBody(RESET_PASSWORD), true, true);
        assertEquals(200, login.statusCode());
        String access = cookie(login, "PA_ACCESS");
        HttpResult otherLogin = request("POST", "/usuario/login", loginBody(RESET_PASSWORD), true, true);
        assertEquals(200, otherLogin.statusCode());
        String otherAccess = cookie(otherLogin, "PA_ACCESS");
        String otherRefresh = cookie(otherLogin, "PA_REFRESH");
        Long currentId = jwtService.extractSessionId(access);
        var currentBefore = sessions.findById(currentId).orElseThrow();
        java.time.Instant currentExpiry = currentBefore.getExpiraEm();
        assertTrue(currentBefore.isLembrarDeMim());
        assertEquals(2, activeSessionCount());

        HttpResult passwordChange = request("POST", "/usuario/alterar-senha", java.util.Map.of(
                "senhaAtual", RESET_PASSWORD, "novaSenha", CHANGED_PASSWORD,
                "confirmarSenha", CHANGED_PASSWORD,
                "desconectarOutrosDispositivos", true, "usuarioId", 999999,
                "sessaoId", 123456), true, true, "PA_ACCESS=" + access);
        assertEquals(200, passwordChange.statusCode());
        assertFalse(passwordChange.cookies().stream()
                .anyMatch(value -> value.startsWith("PA_ACCESS=") || value.startsWith("PA_REFRESH=")));
        assertEquals(200, request("GET", "/usuario/sessao-atual", null, false, true,
                "PA_ACCESS=" + access).statusCode());
        assertEquals(401, request("GET", "/usuario/sessao-atual", null, false, true,
                "PA_ACCESS=" + otherAccess).statusCode());
        assertEquals(401, request("POST", "/usuario/refresh", null, true, true,
                "PA_REFRESH=" + otherRefresh).statusCode());
        assertEquals(204, request("POST", "/usuario/refresh", null, true, true,
                "PA_REFRESH=" + cookie(login, "PA_REFRESH")).statusCode());
        var currentAfter = sessions.findById(currentId).orElseThrow();
        assertEquals(currentExpiry, currentAfter.getExpiraEm());
        assertTrue(currentAfter.isLembrarDeMim());
        assertTrue(encoder.matches(CHANGED_PASSWORD, users.findById(fixture.getId()).orElseThrow().getSenha()));
        assertEquals(1, activeSessionCount());
    }

    @Test
    void checkedPasswordChangeKeepsCallerButRevokesOtherRegularAndPrivilegedSessions() throws Exception {
        verifySessionChangePolicy(true);
    }

    @Test
    void uncheckedPasswordChangeKeepsCallerAndOtherRegularSessionsButRevokesPrivilegedSessions() throws Exception {
        verifySessionChangePolicy(false);
    }

    private void verifySessionChangePolicy(boolean disconnectOthers) throws Exception {
        fixture.getRoles().add(roles.findByNome("ADMIN").orElseThrow());
        fixture.setTelefone("+5511999999999");
        fixture.setTelefoneVerificado(true);
        fixture = users.saveAndFlush(fixture);

        CodigoAutenticacao loginCode = loginCodes.saveAndFlush(new CodigoAutenticacao(
                "2".repeat(64), fixture, java.time.Instant.now().plusSeconds(300)));
        AdminAuthChallenge pending = challenges.saveAndFlush(challenge(AdminAuthChallenge.State.PENDING));
        AdminAuthChallenge verified = challenge(AdminAuthChallenge.State.VERIFIED);
        java.time.Instant completedAt = java.time.Instant.now();
        verified.setEmailVerifiedAt(completedAt);
        verified.setSmsVerifiedAt(completedAt);
        verified.setCompletedAt(completedAt);
        challenges.saveAndFlush(verified);
        var privilegedCredential = adminSessionService.emitir(verified.getId(), testIp);
        assertTrue(adminSessionService.autenticar(privilegedCredential.rawCredential(), testIp).isPresent());

        recoveryService.solicitar(email, testIp);
        var pendingRecovery = recoveries.findAll().stream()
                .filter(item -> item.getUsuario() != null && item.getUsuario().getId().equals(fixture.getId())
                        && item.getTokenHash() != null && item.getConsumidoEm() == null)
                .findFirst().orElseThrow();

        HttpResult currentLogin = request("POST", "/usuario/login", loginBody(OLD_PASSWORD), true, true);
        HttpResult otherLogin = request("POST", "/usuario/login", loginBody(OLD_PASSWORD), true, true);
        String currentAccess = cookie(currentLogin, "PA_ACCESS");
        String currentRefresh = cookie(currentLogin, "PA_REFRESH");
        String otherAccess = cookie(otherLogin, "PA_ACCESS");
        String otherRefresh = cookie(otherLogin, "PA_REFRESH");
        Long currentSessionId = jwtService.extractSessionId(currentAccess);
        Long otherSessionId = jwtService.extractSessionId(otherAccess);
        Instant originalExpiration = sessions.findById(currentSessionId).orElseThrow().getExpiraEm();

        var passwordBody = java.util.Map.of(
                "senhaAtual", OLD_PASSWORD,
                "novaSenha", CHANGED_PASSWORD,
                "confirmarSenha", CHANGED_PASSWORD,
                "desconectarOutrosDispositivos", disconnectOthers,
                "usuarioId", fixture.getId() + 1000,
                "sessaoId", otherSessionId);
        assertEquals(403, request("POST", "/usuario/alterar-senha", passwordBody,
                false, true, "PA_ACCESS=" + currentAccess).statusCode());
        HttpResult passwordChange = request("POST", "/usuario/alterar-senha", passwordBody,
                true, true, "PA_ACCESS=" + currentAccess);
        assertEquals(200, passwordChange.statusCode());
        assertFalse(passwordChange.cookies().stream()
                .anyMatch(value -> value.startsWith("PA_ACCESS=") || value.startsWith("PA_REFRESH=")));

        assertEquals(200, request("GET", "/usuario/sessao-atual", null, false, true,
                "PA_ACCESS=" + currentAccess).statusCode());
        assertEquals(disconnectOthers ? 401 : 200,
                request("GET", "/usuario/sessao-atual", null, false, true,
                        "PA_ACCESS=" + otherAccess).statusCode());
        assertEquals(204, request("POST", "/usuario/refresh", null, true, true,
                "PA_REFRESH=" + currentRefresh).statusCode());
        assertEquals(disconnectOthers ? 401 : 204,
                request("POST", "/usuario/refresh", null, true, true,
                        "PA_REFRESH=" + otherRefresh).statusCode());

        assertEquals(originalExpiration, sessions.findById(currentSessionId).orElseThrow().getExpiraEm());
        assertFalse(sessions.findById(currentSessionId).orElseThrow().isRevogada());
        assertEquals(disconnectOthers,
                sessions.findById(otherSessionId).orElseThrow().isRevogada());
        assertTrue(adminSessionService.autenticar(privilegedCredential.rawCredential(), testIp).isEmpty());
        assertTrue(loginCodes.findById(loginCode.getId()).isEmpty());
        assertEquals(AdminAuthChallenge.State.INVALIDATED,
                challenges.findById(pending.getId()).orElseThrow().getState());
        assertEquals(AdminAuthChallenge.State.INVALIDATED,
                challenges.findById(verified.getId()).orElseThrow().getState());
        assertNotNull(recoveries.findById(pendingRecovery.getId()).orElseThrow().getConsumidoEm());
        assertEquals(disconnectOthers ? 1 : 2, activeSessionCount());
    }

    @Test
    void concurrentRequestsUsingOneRecoveryCredentialHaveExactlyOneWinner() throws Exception {
        assertEquals(202, request("POST", "/usuario/recuperacao-senha/solicitar",
                java.util.Map.of("email", email), true, true).statusCode());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(10_000)).enviarRecuperacaoSenha(eq(email), link.capture());
        String marker = "#redefinir-senha/";
        String token = UriUtils.decode(link.getValue().substring(link.getValue().indexOf(marker) + marker.length()), StandardCharsets.UTF_8);
        var body = java.util.Map.of("token", token, "novaSenha", RESET_PASSWORD, "confirmarSenha", RESET_PASSWORD);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> one = pool.submit(() -> resetStatus(ready, start, body));
            Future<Integer> two = pool.submit(() -> resetStatus(ready, start, body));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int first = one.get(30, TimeUnit.SECONDS);
            int second = two.get(30, TimeUnit.SECONDS);
            assertTrue((first == 200 && second == 400) || (first == 400 && second == 200),
                    "Exactly one concurrent reset must consume the credential.");
        }
        assertTrue(encoder.matches(RESET_PASSWORD, users.findById(fixture.getId()).orElseThrow().getSenha()));
    }

    @Test
    void recoveryIssuanceRacingResetMakesTheOldCredentialUnusable() throws Exception {
        assertEquals(202, request("POST", "/usuario/recuperacao-senha/solicitar",
                java.util.Map.of("email", email), true, true).statusCode());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(10_000)).enviarRecuperacaoSenha(eq(email), link.capture());
        String marker = "#redefinir-senha/";
        String oldToken = UriUtils.decode(link.getValue().substring(link.getValue().indexOf(marker) + marker.length()), StandardCharsets.UTF_8);
        var resetBody = java.util.Map.of("token", oldToken,
                "novaSenha", RESET_PASSWORD, "confirmarSenha", RESET_PASSWORD);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> reset = pool.submit(() -> concurrentHttp(ready, start,
                    "/usuario/recuperacao-senha/redefinir", resetBody));
            Future<?> issuance = pool.submit(() -> {
                ready.countDown(); start.await(); recoveryService.solicitar(email, testIp); return null;
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            int resetStatus = reset.get(30, TimeUnit.SECONDS); issuance.get(30, TimeUnit.SECONDS);
            assertTrue(resetStatus == 200 || resetStatus == 400);
        }
        assertEquals(400, request("POST", "/usuario/recuperacao-senha/redefinir",
                resetBody, true, true).statusCode());
    }

    @Test
    void oldPasswordLoginRacingPasswordChangeCannotLeaveAUsableOldSession() throws Exception {
        HttpResult baseLogin = request("POST", "/usuario/login", loginBody(OLD_PASSWORD), true, true);
        assertEquals(200, baseLogin.statusCode());
        String access = cookie(baseLogin, "PA_ACCESS");
        var changeBody = java.util.Map.of("senhaAtual", OLD_PASSWORD,
                "novaSenha", CHANGED_PASSWORD, "confirmarSenha", CHANGED_PASSWORD,
                "desconectarOutrosDispositivos", true);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<HttpResult> login = pool.submit(() -> concurrentHttpResult(ready, start,
                    "/usuario/login", loginBody(OLD_PASSWORD)));
            Future<HttpResult> change = pool.submit(() -> concurrentHttpResult(ready, start,
                    "/usuario/alterar-senha", changeBody, "PA_ACCESS=" + access));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            HttpResult loginResult = login.get(30, TimeUnit.SECONDS);
            assertEquals(200, change.get(30, TimeUnit.SECONDS).statusCode());
            assertTrue(loginResult.statusCode() == 200 || loginResult.statusCode() == 401);
            if (loginResult.statusCode() == 200) {
                assertEquals(401, request("GET", "/usuario/sessao-atual", null, false, true,
                        "PA_ACCESS=" + cookie(loginResult, "PA_ACCESS")).statusCode());
            }
        }
        assertTrue(encoder.matches(CHANGED_PASSWORD,
                users.findById(fixture.getId()).orElseThrow().getSenha()));
    }

    @Test
    void refreshRotationRacingPasswordChangeCannotRestoreSession() throws Exception {
        HttpResult login = request("POST", "/usuario/login", loginBody(OLD_PASSWORD), true, true);
        String access = cookie(login, "PA_ACCESS"); String refresh = cookie(login, "PA_REFRESH");
        var changeBody = java.util.Map.of("senhaAtual", OLD_PASSWORD,
                "novaSenha", CHANGED_PASSWORD, "confirmarSenha", CHANGED_PASSWORD,
                "desconectarOutrosDispositivos", true);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<HttpResult> rotation = pool.submit(() -> concurrentHttpResult(ready, start,
                    "/usuario/refresh", null, "PA_REFRESH=" + refresh));
            Future<HttpResult> change = pool.submit(() -> concurrentHttpResult(ready, start,
                    "/usuario/alterar-senha", changeBody, "PA_ACCESS=" + access));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            HttpResult rotated = rotation.get(30, TimeUnit.SECONDS);
            assertEquals(200, change.get(30, TimeUnit.SECONDS).statusCode());
            assertTrue(rotated.statusCode() == 204 || rotated.statusCode() == 401);
            assertEquals(401, request("POST", "/usuario/refresh", null, true, true,
                    "PA_REFRESH=" + refresh).statusCode());
            if (rotated.statusCode() == 204) {
                assertEquals(204, request("POST", "/usuario/refresh", null, true, true,
                        "PA_REFRESH=" + cookie(rotated, "PA_REFRESH")).statusCode());
            }
            assertEquals(200, request("GET", "/usuario/sessao-atual", null, false, true,
                    "PA_ACCESS=" + access).statusCode());
        }
    }

    @Test
    void failedRequiredRevocationRollsBackPasswordAndRecoveryState() {
        Long currentSessionId = userSessionService.criarSessao(fixture, true).sessao().getId();
        var credential = new com.portoajuda.aplicacao_osc.entity.RecuperacaoSenha();
        credential.setUsuario(fixture); credential.setTokenHash("b".repeat(64));
        credential.setEmailKey(hmac(email)); credential.setIpKey(hmac(testIp));
        credential.setSolicitadoEm(java.time.Instant.now());
        credential.setExpiraEm(java.time.Instant.now().plusSeconds(1200));
        Long credentialId = recoveries.saveAndFlush(credential).getId();
        String previousHash = users.findById(fixture.getId()).orElseThrow().getSenha();
        doThrow(new IllegalStateException("synthetic revocation failure"))
                .when(adminSessionSpy).revogarTodasDoUsuario(fixture.getId(), "PASSWORD_CHANGED");
        assertThrows(IllegalStateException.class, () -> userService.changePassword(
                OLD_PASSWORD, CHANGED_PASSWORD, CHANGED_PASSWORD, fixture.getId(), currentSessionId, true));
        assertEquals(previousHash, users.findById(fixture.getId()).orElseThrow().getSenha());
        assertNull(recoveries.findById(credentialId).orElseThrow().getConsumidoEm());
    }

    @Test
    void adminSessionIssuanceRacingPasswordChangeCannotLeaveSessionUsable() throws Exception {
        fixture.getRoles().add(roles.findByNome("ADMIN").orElseThrow());
        fixture.setTelefone("+5511999999999"); fixture.setTelefoneVerificado(true);
        fixture = users.saveAndFlush(fixture);
        Long currentSessionId = userSessionService.criarSessao(fixture, true).sessao().getId();
        AdminAuthChallenge verified = challenge(AdminAuthChallenge.State.VERIFIED);
        java.time.Instant now = java.time.Instant.now();
        verified.setEmailVerifiedAt(now); verified.setSmsVerifiedAt(now); verified.setCompletedAt(now);
        challenges.saveAndFlush(verified);
        Integer userId = fixture.getId(); UUID challengeId = verified.getId();
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<AdminPrivilegedSessionService.IssuedCredential> issue = pool.submit(() -> {
                ready.countDown(); start.await(); return adminSessionService.emitir(challengeId, testIp);
            });
            Future<?> change = pool.submit(() -> {
                ready.countDown(); start.await(); userService.changePassword(OLD_PASSWORD,
                        CHANGED_PASSWORD, CHANGED_PASSWORD, userId, currentSessionId, true); return null;
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown(); change.get(30, TimeUnit.SECONDS);
            try {
                var credential = issue.get(30, TimeUnit.SECONDS);
                assertTrue(adminSessionService.autenticar(credential.rawCredential(), testIp).isEmpty());
            } catch (java.util.concurrent.ExecutionException invalidatedChallengeWon) {
                assertNotNull(invalidatedChallengeWon.getCause());
            }
        }
        assertEquals(0, adminSessions.findAll().stream()
                .filter(s -> s.getUsuario().getId().equals(userId) && s.getRevokedAt() == null).count());
    }

    private int resetStatus(CountDownLatch ready, CountDownLatch start, Object body) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return request("POST", "/usuario/recuperacao-senha/redefinir", body, true, true).statusCode();
    }

    private int concurrentHttp(CountDownLatch ready, CountDownLatch start, String path, Object body) throws Exception {
        return concurrentHttpResult(ready, start, path, body).statusCode();
    }

    private HttpResult concurrentHttpResult(CountDownLatch ready, CountDownLatch start,
            String path, Object body, String... cookies) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return request("POST", path, body, true, true, cookies);
    }

    private AdminAuthChallenge challenge(AdminAuthChallenge.State state) {
        AdminAuthChallenge challenge = new AdminAuthChallenge();
        java.time.Instant now = java.time.Instant.now();
        challenge.setId(UUID.randomUUID()); challenge.setUsuario(fixture);
        challenge.setAccountKeyHash("c".repeat(64)); challenge.setIpKeyHash(hmac(testIp));
        challenge.setEmailSnapshotHash("d".repeat(64)); challenge.setPhoneSnapshotHash("e".repeat(64));
        challenge.setEmailCodeHash("f".repeat(64)); challenge.setSmsCodeHash("1".repeat(64));
        challenge.setState(state); challenge.setCreatedAt(now); challenge.setExpiresAt(now.plusSeconds(300));
        return challenge;
    }

    private java.util.Map<String, Object> loginBody(String password) {
        return java.util.Map.of("email", email, "senha", password, "lembrarDeMim", true);
    }

    private long activeSessionCount() {
        return sessions.findAll().stream()
                .filter(s -> s.getUsuario().getId().equals(fixture.getId()) && s.getRevogadaEm() == null)
                .count();
    }

    private String cookie(HttpResult response, String name) {
        return response.cookies().stream()
                .map(value -> value.split(";", 2)[0])
                .filter(value -> value.startsWith(name + "="))
                .map(value -> value.substring(name.length() + 1))
                .findFirst().orElseThrow();
    }

    private HttpResult request(String method, String path, Object body,
            boolean includeCsrf, boolean validCsrf, String... additionalCookies) throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(baseUrl() + path)).header("Accept", "application/json")
                .header("X-Real-IP", testIp);
        java.util.List<String> cookies = new java.util.ArrayList<>();
        if (includeCsrf) {
            var csrf = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl() + "/usuario/csrf"))
                            .GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            var csrfJson = objectMapper.readTree(csrf.body());
            String csrfCookie = csrf.headers().allValues("Set-Cookie").stream()
                    .map(value -> value.split(";", 2)[0])
                    .filter(value -> value.startsWith("XSRF-TOKEN="))
                    .map(value -> value.substring("XSRF-TOKEN=".length()))
                    .findFirst().orElseThrow();
            cookies.add("XSRF-TOKEN=" + csrfCookie);
            if (validCsrf) {
                builder.header(csrfJson.path("headerName").asText(), csrfJson.path("token").asText());
            }
        }
        cookies.addAll(List.of(additionalCookies));
        if (!cookies.isEmpty()) builder.header("Cookie", String.join("; ", cookies));
        if (body == null) {
            builder.method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        }
        var response = http.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.headers().allValues("Set-Cookie"), response.body());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + localPort;
    }

    private record HttpResult(int statusCode, List<String> cookies, String body) {}

    private String validSyntheticCpf() {
        SecureRandom random = new SecureRandom();
        while (true) {
            int[] n = new int[11];
            for (int i = 0; i < 9; i++) n[i] = random.nextInt(10);
            for (int pos = 9; pos < 11; pos++) {
                int sum = 0;
                for (int i = 0; i < pos; i++) sum += n[i] * ((pos + 1) - i);
                int rem = (sum * 10) % 11;
                n[pos] = rem == 10 ? 0 : rem;
            }
            String cpf = java.util.Arrays.stream(n).mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining());
            try { return new Cpf(cpf).valor(); } catch (IllegalArgumentException ignored) { }
        }
    }

    private String hmac(String value) {
        try {
            byte[] key = Base64.getUrlDecoder().decode(pepper);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute synthetic cleanup key.", e);
        }
    }

    private void recordSyntheticFixtureId(Integer id) throws java.io.IOException {
        String ledger = System.getenv("PASSWORD_RECOVERY_TEST_LEDGER");
        if (ledger == null || ledger.isBlank()) {
            throw new IllegalStateException("The opt-in integration test requires a task-local fixture ledger path.");
        }
        java.nio.file.Path path = java.nio.file.Path.of(ledger);
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, id + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }
}
