package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery;
import com.portoajuda.aplicacao_osc.entity.AdminAuthControl;
import com.portoajuda.aplicacao_osc.entity.AdminPrivilegedSession;
import com.portoajuda.aplicacao_osc.entity.AdminSecurityAuditEvent;
import com.portoajuda.aplicacao_osc.entity.PhoneVerificationChallenge;
import com.portoajuda.aplicacao_osc.utils.ConverterCnpj;
import com.portoajuda.aplicacao_osc.utils.ConverterCpf;
import com.portoajuda.aplicacao_osc.utils.ConverterEmail;
import jakarta.persistence.Entity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** Runs only when the caller explicitly supplies a loopback-only disposable PostgreSQL URL. */
class AdminAuthPostgresIntegrationTest {

    private static final String DATABASE_NAME = "porto_admin_auth_test";
    private static final Set<String> NEW_AUTH_ENTITIES = Set.of(
            AdminAuthChallenge.class.getName(),
            AdminAuthDelivery.class.getName(),
            PhoneVerificationChallenge.class.getName(),
            AdminAuthControl.class.getName(),
            AdminSecurityAuditEvent.class.getName(),
            AdminPrivilegedSession.class.getName());

    @Test
    void m3OutboxConstraintsRlsConcurrencyRollbackAndHibernateValidate() throws Exception {
        String url = System.getProperty("admin.auth.pg.test.url");
        String user = System.getProperty("admin.auth.pg.test.user", "postgres");
        String password = System.getProperty("admin.auth.pg.test.password", "");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql://127.0.0.1:")
                && url.endsWith("/" + DATABASE_NAME),
                "Provide an explicitly disposable loopback PostgreSQL database URL.");
        assumeTrue("postgres".equals(user), "M3 must run with the intended local test role.");

        Integer legacyUserId = createPostM1M2Baseline(url, user, password);
        try (Connection connection = connect(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE public.usuarios DROP COLUMN telefone_verificado");
        }
        String migration = readMigration();
        String forcedFailure = migration.replace("COMMIT;",
                "DO $$ BEGIN RAISE EXCEPTION 'forced integration rollback'; END $$; COMMIT;");
        assertFalse(forcedFailure.equals(migration), "Rollback injection point must be found.");
        assertThrows(SQLException.class, () -> executeMigration(url, user, password, forcedFailure));
        assertEquals("true", scalar(url, user, password,
                "SELECT (to_regclass('public.admin_auth_challenges') IS NULL)::text"));
        assertEquals("0", scalar(url, user, password,
                "SELECT count(*)::text FROM information_schema.columns WHERE table_schema='public'"
                        + " AND table_name='usuarios' AND column_name='telefone_verificado'"));
        executeMigration(url, user, password, migration);

        verifySchemaSecurityAndConstraints(url, user, password);
        verifyOutboxWorkersClaimDifferentRows(url, user, password);
        verifyRollback(url, user, password);
        verifyConcurrentChallengeConsumption(url, user, password);
        verifyAtomicCounters(url, user, password);
        validateHibernate(url, user, password, true, false);
        assertEquals("Fictitious admin", scalar(url, user, password,
                "SELECT nome FROM public.usuarios WHERE id=" + legacyUserId));
        assertEquals("false", scalar(url, user, password,
                "SELECT telefone_verificado::text FROM public.usuarios WHERE id=" + legacyUserId));
        String m4 = readM4Migration();
        String forcedM4Failure = m4.replace("COMMIT;",
                "DO $$ BEGIN RAISE EXCEPTION 'forced M4 rollback'; END $$; COMMIT;");
        assertFalse(forcedM4Failure.equals(m4), "M4 rollback injection point must be found.");
        assertThrows(SQLException.class, () -> executeMigration(url, user, password, forcedM4Failure));
        assertEquals("true", scalar(url, user, password,
                "SELECT (to_regclass('public.admin_privileged_sessions') IS NULL)::text"));
        executeMigration(url, user, password, m4);
        verifyM4Schema(url, user, password, legacyUserId);
        validateHibernate(url, user, password, true, true);
        verifyUserDeleteRelationships(url, user, password, legacyUserId);

        long before = count(url, user, password, "admin_auth_challenges");
        assertThrows(SQLException.class, () -> executeMigration(url, user, password, readMigration()));
        assertEquals(before, count(url, user, password, "admin_auth_challenges"),
                "Repeated migration must be rejected without partial changes.");
        long sessionCount = count(url, user, password, "admin_privileged_sessions");
        assertThrows(SQLException.class, () -> executeMigration(url, user, password, readM4Migration()));
        assertEquals(sessionCount, count(url, user, password, "admin_privileged_sessions"),
                "Repeated M4 execution must be rejected without partial changes.");
    }

    private void verifyOutboxWorkersClaimDifferentRows(String url, String user, String password) throws Exception {
        UUID challenge = insertChallenge(url, user, password, AdminAuthChallenge.State.ISSUING.name());
        try (Connection setup = connect(url, user, password)) {
            insertDelivery(setup, UUID.randomUUID(), challenge, "EMAIL", "PENDING", new byte[] {1}, 0, null);
            insertDelivery(setup, UUID.randomUUID(), challenge, "SMS", "PENDING", new byte[] {2}, 0, null);
        }
        String claimSql = """
                SELECT d.id
                  FROM public.admin_auth_deliveries d
                  JOIN public.admin_auth_challenges c ON c.id=d.challenge_id
                 WHERE c.state='ISSUING' AND c.expires_at>CURRENT_TIMESTAMP AND d.attempt_count<3
                   AND (d.state='PENDING' OR (d.state='PROCESSING' AND d.lease_until<=CURRENT_TIMESTAMP))
                 ORDER BY d.created_at,d.id
                 FOR UPDATE OF d SKIP LOCKED LIMIT 1
                """;
        try (Connection first = connect(url, user, password);
                Connection second = connect(url, user, password)) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            UUID firstDelivery = lockOne(first, claimSql);
            UUID secondDelivery = lockOne(second, claimSql);
            assertNotNull(firstDelivery);
            assertNotNull(secondDelivery);
            org.junit.jupiter.api.Assertions.assertNotEquals(firstDelivery, secondDelivery,
                    "SKIP LOCKED must let two application instances claim distinct rows.");
            first.rollback();
            second.rollback();
        }
    }

    private UUID lockOne(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getObject(1, UUID.class) : null;
        }
    }

    private Integer createPostM1M2Baseline(String url, String user, String password) throws SQLException {
        StandardServiceRegistry registry = buildRegistry(url, user, password, "create");
        try {
            MetadataSources sources = entitySources(registry, false, false);
            try (SessionFactory ignored = sources.buildMetadata().buildSessionFactory()) {
                // Hibernate creates the existing mapped application schema as an isolated M1/M2 analogue.
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
        try (Connection c = connect(url, user, password); Statement s = c.createStatement()) {
            s.executeUpdate("""
                    INSERT INTO public.usuarios
                        (cpf,data_nascimento,nome,genero,email,senha_hash,ativo,email_verificado,
                         telefone_verificado,data_criacao)
                    VALUES ('12345678901','1990-01-01','Fictitious admin','FEMININO',
                            'admin@example.invalid','test-password-hash',TRUE,TRUE,FALSE,CURRENT_TIMESTAMP)
                    """);
            return Integer.valueOf(scalar(s, "SELECT id::text FROM public.usuarios"
                    + " WHERE email='admin@example.invalid'"));
        }
    }

    private void validateHibernate(String url, String user, String password,
            boolean includeM3Entities, boolean includeM4Entity) {
        StandardServiceRegistry registry = buildRegistry(url, user, password, "validate");
        try (SessionFactory ignored = entitySources(registry, includeM3Entities, includeM4Entity)
                .buildMetadata().buildSessionFactory()) {
            assertNotNull(ignored);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private StandardServiceRegistry buildRegistry(String url, String user, String password, String ddlMode) {
        return new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.username", user)
                .applySetting("hibernate.connection.password", password)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.hbm2ddl.auto", ddlMode)
                .applySetting("hibernate.show_sql", false)
                .applySetting("hibernate.format_sql", false)
                .build();
    }

    private MetadataSources entitySources(
            StandardServiceRegistry registry, boolean includeM3Entities, boolean includeM4Entity) {
        MetadataSources sources = new MetadataSources(registry);
        sources.addAnnotatedClass(ConverterCnpj.class);
        sources.addAnnotatedClass(ConverterCpf.class);
        sources.addAnnotatedClass(ConverterEmail.class);
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("com.portoajuda.aplicacao_osc.entity")) {
            try {
                Class<?> entity = Class.forName(candidate.getBeanClassName());
                boolean m4Session = AdminPrivilegedSession.class.getName().equals(entity.getName());
                if ((!NEW_AUTH_ENTITIES.contains(entity.getName()) || includeM3Entities)
                        && (!m4Session || includeM4Entity)) {
                    sources.addAnnotatedClass(entity);
                }
            } catch (ClassNotFoundException error) {
                throw new IllegalStateException("Could not load JPA entity for isolated schema validation.", error);
            }
        }
        return sources;
    }

    private void verifyM4Schema(String url, String user, String password, Integer userId) throws SQLException {
        try (Connection c = connect(url, user, password); Statement s = c.createStatement()) {
            assertEquals("true", scalar(s, "SELECT relrowsecurity::text FROM pg_class"
                    + " WHERE oid='public.admin_privileged_sessions'::regclass"));
            assertEquals("0", scalar(s, "SELECT count(*)::text FROM pg_policy"
                    + " WHERE polrelid='public.admin_privileged_sessions'::regclass"));
            assertEquals("postgres", scalar(s, "SELECT pg_get_userbyid(relowner) FROM pg_class"
                    + " WHERE oid='public.admin_privileged_sessions'::regclass"));
            for (String role : List.of("anon", "authenticated")) {
                for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE",
                        "REFERENCES", "TRIGGER", "MAINTAIN")) {
                    assertEquals("false", scalar(s, "SELECT has_table_privilege('" + role
                            + "','public.admin_privileged_sessions','" + privilege + "')::text"));
                }
            }
            for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
                assertEquals("true", scalar(s, "SELECT has_table_privilege(current_user,'public.admin_privileged_sessions','"
                        + privilege + "')::text"));
            }

            UUID challengeId = UUID.randomUUID();
            try (PreparedStatement challenge = c.prepareStatement("""
                    INSERT INTO public.admin_auth_challenges
                        (id,usuario_id,account_key_hash,email_snapshot_hash,phone_snapshot_hash,
                         state,created_at,expires_at,email_verified_at,sms_verified_at,completed_at)
                    VALUES (?, ?, repeat('d',64), repeat('e',64), repeat('f',64), 'VERIFIED',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP+INTERVAL '5 minutes',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                challenge.setObject(1, challengeId);
                challenge.setInt(2, userId);
                challenge.executeUpdate();
            }
            UUID sessionId = UUID.randomUUID();
            try (PreparedStatement session = c.prepareStatement("""
                    INSERT INTO public.admin_privileged_sessions
                        (id,usuario_id,challenge_id,credential_hash,created_at,last_activity_at,
                         absolute_expires_at,idle_expires_at)
                    VALUES (?, ?, ?, repeat('a',64), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP+INTERVAL '2 hours', CURRENT_TIMESTAMP+INTERVAL '20 minutes')
                    """)) {
                session.setObject(1, sessionId);
                session.setInt(2, userId);
                session.setObject(3, challengeId);
                session.executeUpdate();
            }
            assertEquals("64", scalar(s, "SELECT length(credential_hash)::text"
                    + " FROM public.admin_privileged_sessions WHERE id='" + sessionId + "'"));
            assertThrows(SQLException.class, () -> {
                try (Statement duplicate = c.createStatement()) {
                    duplicate.executeUpdate("INSERT INTO public.admin_privileged_sessions"
                            + " (id,usuario_id,challenge_id,credential_hash,created_at,last_activity_at,"
                            + "absolute_expires_at,idle_expires_at) VALUES ('" + UUID.randomUUID() + "',"
                            + userId + ", '" + challengeId + "',repeat('b',64),CURRENT_TIMESTAMP,"
                            + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '2 hours',"
                            + "CURRENT_TIMESTAMP+INTERVAL '20 minutes')");
                }
            }, "a verified challenge can issue at most one session");
            assertThrows(SQLException.class, () -> {
                try (Statement invalid = c.createStatement()) {
                    invalid.executeUpdate("INSERT INTO public.admin_privileged_sessions"
                            + " (id,usuario_id,challenge_id,credential_hash,created_at,last_activity_at,"
                            + "absolute_expires_at,idle_expires_at) VALUES ('" + UUID.randomUUID() + "',"
                            + userId + ", '" + UUID.randomUUID() + "',repeat('c',64),CURRENT_TIMESTAMP,"
                            + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '20 minutes',"
                            + "CURRENT_TIMESTAMP+INTERVAL '21 minutes')");
                }
            }, "idle expiry cannot exceed absolute expiry");

            UUID secondChallengeId = UUID.randomUUID();
            try (PreparedStatement challenge = c.prepareStatement("""
                    INSERT INTO public.admin_auth_challenges
                        (id,usuario_id,account_key_hash,state,created_at,expires_at,
                         email_verified_at,sms_verified_at,completed_at)
                    VALUES (?, ?, repeat('1',64), 'VERIFIED', CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP+INTERVAL '5 minutes',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """)) {
                challenge.setObject(1, secondChallengeId);
                challenge.setInt(2, userId);
                challenge.executeUpdate();
            }
            assertThrows(SQLException.class, () -> {
                try (PreparedStatement duplicateCredential = c.prepareStatement("""
                        INSERT INTO public.admin_privileged_sessions
                            (id,usuario_id,challenge_id,credential_hash,created_at,last_activity_at,
                             absolute_expires_at,idle_expires_at)
                        VALUES (?, ?, ?, repeat('a',64), CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
                                CURRENT_TIMESTAMP+INTERVAL '2 hours',CURRENT_TIMESTAMP+INTERVAL '20 minutes')
                        """)) {
                    duplicateCredential.setObject(1, UUID.randomUUID());
                    duplicateCredential.setInt(2, userId);
                    duplicateCredential.setObject(3, secondChallengeId);
                    duplicateCredential.executeUpdate();
                }
            }, "credential hashes must be unique");
        }
    }

    private void verifySchemaSecurityAndConstraints(String url, String user, String password) throws SQLException {
        try (Connection c = connect(url, user, password); Statement s = c.createStatement()) {
            assertEquals("postgres", scalar(s, "SELECT current_user"));
            assertEquals("true", scalar(s, "SELECT rolbypassrls::text FROM pg_roles WHERE rolname=current_user"));
            assertEquals("false", scalar(s, "SELECT rolsuper::text FROM pg_roles WHERE rolname=current_user"));
            List<String> authTables = List.of("admin_auth_challenges", "admin_auth_deliveries",
                    "phone_verification_challenges", "admin_auth_controls", "admin_security_audit_events");
            for (String table : authTables) {
                assertEquals("true", scalar(s, "SELECT relrowsecurity::text FROM pg_class WHERE oid='public."
                        + table + "'::regclass"), table + " RLS enabled");
                assertEquals("0", scalar(s, "SELECT count(*)::text FROM pg_policy WHERE polrelid='public."
                        + table + "'::regclass"), table + " has no RLS policies");
                assertEquals("postgres", scalar(s, "SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='public."
                        + table + "'::regclass"), table + " owner");
                for (String role : List.of("anon", "authenticated")) {
                    for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE",
                            "REFERENCES", "TRIGGER", "MAINTAIN")) {
                        assertEquals("false", scalar(s, "SELECT has_table_privilege('" + role
                                + "','public." + table + "','" + privilege + "')::text"),
                                role + " must not have " + privilege + " on " + table);
                    }
                }
                for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
                    assertEquals("true", scalar(s, "SELECT has_table_privilege(current_user,'public." + table
                            + "','" + privilege + "')::text"), "backend CRUD on " + table);
                }
            }

            assertEquals("true", scalar(s, "SELECT i.indisvalid::text FROM pg_index i"
                    + " WHERE i.indexrelid='public.idx_admin_auth_deliveries_claim'::regclass"));
            assertEquals("true", scalar(s, "SELECT i.indisunique::text FROM pg_index i"
                    + " WHERE i.indexrelid='public.uq_admin_auth_delivery_challenge_channel'::regclass"));
            assertEquals("SET NULL", scalar(s, "SELECT delete_rule FROM information_schema.referential_constraints"
                    + " WHERE constraint_name='admin_auth_challenges_usuario_id_fkey'"));
            assertEquals("CASCADE", scalar(s, "SELECT delete_rule FROM information_schema.referential_constraints"
                    + " WHERE constraint_name='admin_auth_deliveries_challenge_id_fkey'"));

            UUID challengeId = insertChallenge(c, AdminAuthChallenge.State.PENDING.name());
            insertDelivery(c, UUID.randomUUID(), challengeId, "EMAIL", "PENDING", new byte[] {1, 2, 3}, 0, null);
            assertThrows(SQLException.class, () -> insertDelivery(c, UUID.randomUUID(), challengeId,
                    "EMAIL", "PENDING", new byte[] {4, 5, 6}, 0, null), "unique challenge/channel constraint");
            assertThrows(SQLException.class, () -> insertDelivery(c, UUID.randomUUID(), UUID.randomUUID(),
                    "SMS", "PENDING", new byte[] {1}, 0, null), "delivery foreign key");
            assertThrows(SQLException.class, () -> insertDelivery(c, UUID.randomUUID(), challengeId,
                    "INVALID", "PENDING", new byte[] {1}, 0, null), "channel check");
            assertThrows(SQLException.class, () -> insertDelivery(c, UUID.randomUUID(), challengeId,
                    "SMS", "PENDING", new byte[] {1}, 4, null), "attempt count check");
        }
    }

    private void verifyUserDeleteRelationships(String url, String user, String password, Integer userId)
            throws SQLException {
        UUID challengeId = UUID.randomUUID();
        UUID phoneChallengeId = UUID.randomUUID();
        try (Connection c = connect(url, user, password); Statement s = c.createStatement()) {
            s.executeUpdate("""
                    INSERT INTO public.admin_auth_challenges
                        (id,usuario_id,account_key_hash,state,created_at,expires_at)
                    VALUES ('""" + challengeId + "'," + userId + ",repeat('b',64),'FAILED',"
                    + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 minute')");
            s.executeUpdate("""
                    INSERT INTO public.phone_verification_challenges
                        (id,usuario_id,phone_snapshot_hash,attempts,state,created_at,expires_at)
                    VALUES ('""" + phoneChallengeId + "'," + userId + ",repeat('c',64),0,'FAILED',"
                    + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 minute')");
            s.executeUpdate("INSERT INTO public.admin_security_audit_events"
                    + " (id,event_type,usuario_id_snapshot,occurred_at) VALUES ('" + UUID.randomUUID()
                    + "','TEST_USER_SNAPSHOT'," + userId + ",CURRENT_TIMESTAMP)");
            s.executeUpdate("DELETE FROM public.usuarios WHERE id=" + userId);
            assertEquals("", scalar(s, "SELECT COALESCE(usuario_id::text,'')"
                    + " FROM public.admin_auth_challenges WHERE id='" + challengeId + "'"));
            assertEquals("0", scalar(s, "SELECT count(*)::text FROM public.phone_verification_challenges"
                    + " WHERE id='" + phoneChallengeId + "'"));
            assertEquals("1", scalar(s, "SELECT count(*)::text FROM public.admin_security_audit_events"
                    + " WHERE usuario_id_snapshot=" + userId));
            assertEquals("0", scalar(s, "SELECT count(*)::text FROM public.admin_privileged_sessions"
                    + " WHERE usuario_id=" + userId),
                    "Deleting a user must remove associated privileged sessions.");
        }
    }

    private void verifyRollback(String url, String user, String password) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = connect(url, user, password)) {
            c.setAutoCommit(false);
            insertChallenge(c, id, AdminAuthChallenge.State.PENDING.name());
            c.rollback();
        }
        assertEquals("0", scalar(url, user, password,
                "SELECT count(*)::text FROM public.admin_auth_challenges WHERE id='" + id + "'"));
    }

    private void verifyConcurrentChallengeConsumption(String url, String user, String password) throws Exception {
        UUID id = insertChallenge(url, user, password, AdminAuthChallenge.State.PENDING.name());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(pool.submit(() -> {
                    try (Connection c = connect(url, user, password)) {
                        c.setAutoCommit(false);
                        ready.countDown();
                        go.await();
                        String state;
                        try (PreparedStatement ps = c.prepareStatement(
                                "SELECT state FROM public.admin_auth_challenges WHERE id=? FOR UPDATE")) {
                            ps.setObject(1, id);
                            try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                state = rs.getString(1);
                            }
                        }
                        boolean consumed = "PENDING".equals(state);
                        if (consumed) {
                            try (PreparedStatement ps = c.prepareStatement(
                                    "UPDATE public.admin_auth_challenges SET state='VERIFIED', completed_at=CURRENT_TIMESTAMP"
                                            + " WHERE id=? AND state='PENDING'")) {
                                ps.setObject(1, id);
                                consumed = ps.executeUpdate() == 1;
                            }
                        }
                        c.commit();
                        return consumed;
                    }
                }));
            }
            ready.await();
            go.countDown();
            int consumed = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) consumed++;
            }
            assertEquals(1, consumed, "Only one concurrent verifier may consume a challenge.");
        } finally {
            pool.shutdownNow();
        }
    }

    private void verifyAtomicCounters(String url, String user, String password) throws Exception {
        int workers = 20;
        String key = UUID.randomUUID().toString().replace("-", "").repeat(2);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<?>> increments = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                increments.add(pool.submit(() -> {
                    try (Connection c = connect(url, user, password);
                            PreparedStatement ps = c.prepareStatement("""
                                    INSERT INTO public.admin_auth_controls
                                        (control_key,control_type,counter,window_started_at,updated_at)
                                    VALUES (?,'ACCOUNT_FAILURE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                                    ON CONFLICT (control_key) DO UPDATE SET
                                        counter=admin_auth_controls.counter+1,
                                        updated_at=CURRENT_TIMESTAMP
                                    """)) {
                        ready.countDown();
                        go.await();
                        ps.setString(1, key);
                        ps.executeUpdate();
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                }));
            }
            ready.await();
            go.countDown();
            for (Future<?> increment : increments) increment.get();
            assertEquals(Integer.toString(workers), scalar(url, user, password,
                    "SELECT counter::text FROM public.admin_auth_controls WHERE control_key='" + key + "'"));
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID insertChallenge(String url, String user, String password, String state) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = connect(url, user, password)) {
            insertChallenge(c, id, state);
        }
        return id;
    }

    private UUID insertChallenge(Connection c, String state) throws SQLException {
        return insertChallenge(c, UUID.randomUUID(), state);
    }

    private UUID insertChallenge(Connection c, UUID id, String state) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO public.admin_auth_challenges
                    (id,account_key_hash,state,created_at,expires_at)
                VALUES (?,repeat('a',64),?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '5 minutes')
                """)) {
            ps.setObject(1, id);
            ps.setString(2, state);
            ps.executeUpdate();
        }
        return id;
    }

    private void insertDelivery(Connection c, UUID id, UUID challengeId, String channel, String state,
            byte[] payload, int attempts, Instant lease) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO public.admin_auth_deliveries
                    (id,challenge_id,channel,encrypted_code,state,attempt_count,lease_until,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, challengeId);
            ps.setString(3, channel);
            ps.setBytes(4, payload);
            ps.setString(5, state);
            ps.setInt(6, attempts);
            ps.setObject(7, lease == null ? null : java.sql.Timestamp.from(lease));
            ps.executeUpdate();
        }
    }

    private long count(String url, String user, String password, String table) throws SQLException {
        return Long.parseLong(scalar(url, user, password, "SELECT count(*)::text FROM public." + table));
    }

    private String scalar(String url, String user, String password, String sql) throws SQLException {
        try (Connection c = connect(url, user, password); Statement s = c.createStatement()) {
            return scalar(s, sql);
        }
    }

    private String scalar(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) throw new SQLException("Expected scalar result.");
            return rs.getString(1);
        }
    }

    private Connection connect(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private String readMigration() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/db/manual/20261105_admin_auth_foundation.sql")) {
            if (stream == null) throw new IOException("M3 resource is missing from the test classpath.");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readM4Migration() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/db/manual/20261106_admin_privileged_sessions.sql")) {
            if (stream == null) throw new IOException("M4 resource is missing from the test classpath.");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void executeMigration(String url, String user, String password, String sql) throws SQLException {
        try (Connection c = connect(url, user, password); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
