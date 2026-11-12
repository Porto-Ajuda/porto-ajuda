package com.portoajuda.aplicacao_osc.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs only against a caller-specified disposable loopback PostgreSQL database.
 */
class AdminRbacPostgresIntegrationTest {

    private static final String DATABASE_NAME = "porto_admin_auth_test";

    @Test
    void schemaRlsPrivilegesAndAuditAppendOnlyMatchTheMigrationContract() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());

        assertEquals("postgres", jdbc.queryForObject("SELECT current_user", String.class));
        assertFalse(jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname=current_user", Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT rolbypassrls FROM pg_roles WHERE rolname=current_user", Boolean.class));
        assertEquals("SET NULL", jdbc.queryForObject("""
                SELECT delete_rule FROM information_schema.referential_constraints
                 WHERE constraint_name='admin_assignments_usuario_id_fkey'
                """, String.class));
        assertTrue(jdbc.queryForObject("""
                SELECT relrowsecurity FROM pg_class WHERE oid='public.admin_assignments'::regclass
                """, Boolean.class));
        assertTrue(jdbc.queryForObject("""
                SELECT relrowsecurity FROM pg_class WHERE oid='public.admin_operation_audit_events'::regclass
                """, Boolean.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM pg_policy
                 WHERE polrelid IN ('public.admin_assignments'::regclass,
                                    'public.admin_operation_audit_events'::regclass)
                """, Integer.class));
        assertEquals("postgres", jdbc.queryForObject("""
                SELECT pg_get_userbyid(relowner) FROM pg_class
                 WHERE oid='public.admin_operation_audit_events'::regclass
                """, String.class));
        assertTrue(jdbc.queryForObject("""
                SELECT bool_and(NOT has_table_privilege(role_name, table_name, privilege_name))
                  FROM (VALUES ('anon'), ('authenticated')) AS roles(role_name)
                  CROSS JOIN (VALUES ('public.admin_assignments'),
                                     ('public.admin_operation_audit_events')) AS tables(table_name)
                  CROSS JOIN (VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'), ('TRUNCATE'))
                                     AS privileges(privilege_name)
                """, Boolean.class));
        String profileConstraint = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                 WHERE conrelid='public.admin_assignments'::regclass
                   AND conname='ck_admin_assignment_profile'
                """, String.class);
        for (String profile : List.of("SUPER_ADMIN", "PLATFORM_ADMIN", "MODERATOR", "AUDITOR")) {
            assertTrue(profileConstraint.contains(profile), profile);
        }
        assertTrue(jdbc.queryForObject("""
                SELECT indisunique AND indisvalid AND indpred IS NOT NULL
                  FROM pg_index WHERE indexrelid='public.uq_admin_assignments_one_active_per_user'::regclass
                """, Boolean.class));

        assertThrows(SQLException.class, () -> executeStatement(jdbc,
                "UPDATE public.admin_operation_audit_events SET reason=reason WHERE false"));
        assertThrows(SQLException.class, () -> executeStatement(jdbc,
                "DELETE FROM public.admin_operation_audit_events WHERE false"));
        assertThrows(SQLException.class, () -> executeStatement(jdbc,
                "TRUNCATE public.admin_operation_audit_events"));
    }

    @Test
    void operationAndLifecycleAuditWritersBindPostgresTimestampsAndRedactSecrets() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        AdminAssignmentRepository assignments = new AdminAssignmentRepository(jdbc);
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);
        AdminOperationAuditService audit = new AdminOperationAuditService(jdbc, authorization);
        String targetId = UUID.randomUUID().toString();
        AdminActor actor = new AdminActor(1, UUID.randomUUID(), AdminProfile.SUPER_ADMIN);

        audit.record(actor, AdminPermission.ADMIN_MANAGE, "INTEGRATION_AUDIT_WRITE",
                "USER", targetId,
                "PA-12345 PA_ADMIN_SESSION=LOCAL-COOKIE-SECRET OTP=123456 Bearer LOCAL-BEARER-SECRET",
                AdminProfile.MODERATOR, "previous", "next");

        String persistedReason = jdbc.queryForObject("""
                SELECT reason FROM public.admin_operation_audit_events
                 WHERE action='INTEGRATION_AUDIT_WRITE' AND target_id=?
                """, String.class, targetId);
        assertFalse(persistedReason.contains("LOCAL-COOKIE-SECRET"));
        assertFalse(persistedReason.contains("LOCAL-BEARER-SECRET"));
        assertFalse(persistedReason.contains("123456"));
        assertTrue(persistedReason.contains("[REDACTED]"));

        String lifecycleTarget = UUID.randomUUID().toString();
        audit.recordLifecycle(null, "SYSTEM", AdminProfile.SUPER_ADMIN,
                "INTEGRATION_ACCOUNT_CLEANUP", lifecycleTarget, "PA-54321");
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM public.admin_operation_audit_events
                 WHERE action='INTEGRATION_ACCOUNT_CLEANUP' AND target_id=?
                """, Integer.class, lifecycleTarget));
    }

    @Test
    void advisoryLockSerializesTwoConcurrentAttemptsToRemoveTheLastSuperAdmins() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        AdminAssignmentRepository assignments = new AdminAssignmentRepository(jdbc);
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);
        AdminRbacInvariantService invariant = new AdminRbacInvariantService(assignments, authorization);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        List<ExistingSuperAdmin> existingSuperAdmins = jdbc.query("""
                SELECT a.id, a.reason, a.last_modified_at, a.last_modified_by_user_id_snapshot
                  FROM public.admin_assignments a
                  JOIN public.usuarios u ON u.id=a.usuario_id
                  JOIN public.usuario_roles ur ON ur.id_usuario=u.id
                  JOIN public.roles r ON r.id=ur.id_role
                 WHERE a.profile='SUPER_ADMIN' AND a.status='ACTIVE'
                   AND u.ativo=TRUE AND r.nome='ADMIN'
                """, (row, index) -> new ExistingSuperAdmin(
                row.getObject("id", UUID.class), row.getString("reason"),
                row.getTimestamp("last_modified_at").toInstant(),
                nullableInt(row, "last_modified_by_user_id_snapshot")));
        assumeTrue(!existingSuperAdmins.isEmpty(), "Bootstrap at least one active SUPER_ADMIN in the disposable DB.");

        Integer firstUser = null;
        Integer secondUser = null;
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch secondAttemptsLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try {
            for (ExistingSuperAdmin existing : existingSuperAdmins) {
                jdbc.update("""
                        UPDATE public.admin_assignments
                           SET status='REVOKED', revoked_at=now(), last_modified_at=now(),
                               reason='rbac integration snapshot', version=version+1
                         WHERE id=?
                        """, existing.id());
            }

            firstUser = createAdminUser(jdbc, "lock-first");
            secondUser = createAdminUser(jdbc, "lock-second");
            AdminAssignment firstAssignment = insertAssignment(assignments, firstUser, AdminProfile.SUPER_ADMIN);
            AdminAssignment secondAssignment = insertAssignment(assignments, secondUser, AdminProfile.SUPER_ADMIN);
            assertEquals(2, assignments.countActiveSuperAdmins());

            Integer firstTarget = firstUser;
            Integer secondTarget = secondUser;
            Future<Boolean> first = workers.submit(() -> transaction.execute(status -> {
                jdbc.execute("SET LOCAL application_name='rbac-invariant-first'");
                invariant.lockEligibilityChanges();
                boolean allowed = invariant.canRemoveEligibility(firstTarget);
                if (allowed) {
                    assignments.revoke(firstAssignment.id(), null, "PA-LOCK-1", Instant.now());
                }
                firstHoldsLock.countDown();
                await(releaseFirst);
                return allowed;
            }));

            assertTrue(firstHoldsLock.await(10, TimeUnit.SECONDS), "first connection acquired the advisory lock");
            Future<Boolean> second = workers.submit(() -> transaction.execute(status -> {
                jdbc.execute("SET LOCAL application_name='rbac-invariant-second'");
                secondAttemptsLock.countDown();
                invariant.lockEligibilityChanges();
                boolean allowed = invariant.canRemoveEligibility(secondTarget);
                if (allowed) {
                    assignments.revoke(secondAssignment.id(), null, "PA-LOCK-2", Instant.now());
                }
                return allowed;
            }));

            assertTrue(secondAttemptsLock.await(10, TimeUnit.SECONDS), "second connection began lock acquisition");
            assertTrue(waitingForAdvisoryLock(jdbc, "rbac-invariant-second", 10),
                    "second PostgreSQL connection must wait on the shared advisory lock");
            releaseFirst.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertFalse(second.get(10, TimeUnit.SECONDS));
            assertEquals(1, assignments.countActiveSuperAdmins());
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
            if (firstUser != null || secondUser != null) {
                for (Integer id : new Integer[] { firstUser, secondUser }) {
                    if (id != null) {
                        jdbc.update("DELETE FROM public.admin_assignments WHERE usuario_id_snapshot=?", id);
                        jdbc.update("DELETE FROM public.usuario_roles WHERE id_usuario=?", id);
                        jdbc.update("DELETE FROM public.usuarios WHERE id=?", id);
                    }
                }
            }
            for (ExistingSuperAdmin existing : existingSuperAdmins) {
                jdbc.update("""
                        UPDATE public.admin_assignments
                           SET status='ACTIVE', revoked_at=NULL, reason=?, last_modified_at=?,
                               last_modified_by_user_id_snapshot=?, version=version+1
                         WHERE id=?
                        """, existing.reason(), Timestamp.from(existing.lastModifiedAt()),
                        existing.lastModifiedBy(), existing.id());
            }
        }
    }

    @Test
    void mfaIneligibleSuperAdminDoesNotCountAsTheLastEligibleSuperAdmin() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        AdminAssignmentRepository assignments = new AdminAssignmentRepository(jdbc);
        AdminRbacInvariantService invariant = new AdminRbacInvariantService(
                assignments, new AdminAuthorizationService(assignments, true));
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        List<ExistingSuperAdmin> existing = jdbc.query("""
                SELECT a.id, a.reason, a.last_modified_at, a.last_modified_by_user_id_snapshot
                  FROM public.admin_assignments a
                  JOIN public.usuarios u ON u.id=a.usuario_id
                  JOIN public.usuario_roles ur ON ur.id_usuario=u.id
                  JOIN public.roles r ON r.id=ur.id_role
                 WHERE a.profile='SUPER_ADMIN' AND a.status='ACTIVE'
                   AND u.ativo=TRUE AND r.nome='ADMIN'
                """, (row, index) -> new ExistingSuperAdmin(
                row.getObject("id", UUID.class), row.getString("reason"),
                row.getTimestamp("last_modified_at").toInstant(),
                nullableInt(row, "last_modified_by_user_id_snapshot")));
        assumeTrue(!existing.isEmpty(), "Bootstrap an active SUPER_ADMIN in the disposable DB.");
        Integer eligibleUser = null;
        Integer ineligibleUser = null;
        try {
            for (ExistingSuperAdmin assignment : existing) {
                jdbc.update("""
                        UPDATE public.admin_assignments
                           SET status='REVOKED', revoked_at=now(), last_modified_at=now(),
                               reason='rbac eligibility snapshot', version=version+1
                         WHERE id=?
                        """, assignment.id());
            }
            eligibleUser = createAdminUser(jdbc, "eligible-super");
            ineligibleUser = createAdminUser(jdbc, "mfa-ineligible-super");
            jdbc.update("UPDATE public.usuarios SET telefone='invalid' WHERE id=?", ineligibleUser);
            insertAssignment(assignments, eligibleUser, AdminProfile.SUPER_ADMIN);
            insertAssignment(assignments, ineligibleUser, AdminProfile.SUPER_ADMIN);

            assertEquals(2, assignments.countActiveSuperAdmins());
            assertEquals(1, assignments.countEligibleActiveSuperAdmins());
            Integer eligibleId = eligibleUser;
            Integer ineligibleId = ineligibleUser;
            boolean canRemoveEligible = Boolean.TRUE.equals(
                    transaction.execute(status -> invariant.canRemoveEligibility(eligibleId)));

            boolean canRemoveIneligible = Boolean.TRUE.equals(
                    transaction.execute(status -> invariant.canRemoveEligibility(ineligibleId)));

            assertFalse(canRemoveEligible);
            assertTrue(canRemoveIneligible);
        } finally {
            for (Integer id : new Integer[] { eligibleUser, ineligibleUser }) {
                if (id != null) {
                    jdbc.update("DELETE FROM public.admin_assignments WHERE usuario_id_snapshot=?", id);
                    jdbc.update("DELETE FROM public.usuario_roles WHERE id_usuario=?", id);
                    jdbc.update("DELETE FROM public.usuarios WHERE id=?", id);
                }
            }
            for (ExistingSuperAdmin assignment : existing) {
                jdbc.update("""
                        UPDATE public.admin_assignments
                           SET status='ACTIVE', revoked_at=NULL, reason=?, last_modified_at=?,
                               last_modified_by_user_id_snapshot=?, version=version+1
                         WHERE id=?
                        """, assignment.reason(), Timestamp.from(assignment.lastModifiedAt()),
                        assignment.lastModifiedBy(), assignment.id());
            }
        }
    }

    private DriverManagerDataSource dataSource() {
        String url = System.getProperty("admin.rbac.pg.test.url");
        String user = System.getProperty("admin.rbac.pg.test.user", "postgres");
        String password = System.getProperty("admin.rbac.pg.test.password", "");
        assumeTrue(url != null
                && url.startsWith("jdbc:postgresql://127.0.0.1:")
                && url.split("\\?", 2)[0].endsWith("/" + DATABASE_NAME),
                "Set admin.rbac.pg.test.url to the explicitly disposable loopback PostgreSQL database.");
        assumeTrue("postgres".equals(user), "The isolated migration owner role must be postgres.");
        String safeLocalUrl = url.contains("sslmode=")
                ? url
                : url + (url.contains("?") ? "&" : "?") + "sslmode=disable&connectTimeout=5&socketTimeout=15";
        return new DriverManagerDataSource(safeLocalUrl, user, password);
    }

    private int createAdminUser(JdbcTemplate jdbc, String suffix) {
        // Disposable databases may contain fixtures inserted with explicit IDs; keep IDENTITY ahead of MAX(id).
        jdbc.queryForObject("""
                SELECT setval(
                    pg_get_serial_sequence('public.usuarios', 'id'),
                    COALESCE((SELECT MAX(id) FROM public.usuarios), 1),
                    EXISTS (SELECT 1 FROM public.usuarios))
                """, Long.class);
        String email = "rbac-it-" + suffix + "-" + UUID.randomUUID() + "@example.invalid";
        String cpf = String.format("%011d", Long.remainderUnsigned(
                UUID.randomUUID().getMostSignificantBits(), 100_000_000_000L));
        Integer userId = jdbc.queryForObject("""
                INSERT INTO public.usuarios (
                    cpf, data_nascimento, nome, genero, email, telefone, senha_hash,
                    ativo, email_verificado, telefone_verificado, data_criacao
                ) VALUES (?, '1990-01-01', 'RBAC integration fixture', 'FEMININO', ?,
                          '5511912345678', 'test-only-hash', TRUE, TRUE, TRUE, now())
                RETURNING id
                """, Integer.class, cpf, email);
        Integer roleId = jdbc.query("SELECT id FROM public.roles WHERE nome='ADMIN'",
                (row, index) -> row.getInt(1)).stream().findFirst()
                .orElseGet(() -> jdbc.queryForObject("INSERT INTO public.roles (nome) VALUES ('ADMIN') RETURNING id",
                        Integer.class));
        jdbc.update("INSERT INTO public.usuario_roles (id_usuario,id_role) VALUES (?,?)", userId, roleId);
        return userId;
    }

    private AdminAssignment insertAssignment(
            AdminAssignmentRepository repository,
            Integer userId,
            AdminProfile profile) {
        Instant now = Instant.now();
        AdminAssignment assignment = new AdminAssignment(
                UUID.randomUUID(), userId, userId, profile, AdminAssignmentStatus.ACTIVE,
                now, null, now, null, null, "PA-TEST-1");
        repository.insert(assignment);
        return assignment;
    }

    private boolean waitingForAdvisoryLock(JdbcTemplate jdbc, String applicationName, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                     WHERE application_name=? AND wait_event_type='Lock' AND wait_event='advisory'
                    """, Integer.class, applicationName);
            if (waiting != null && waiting > 0)
                return true;
            Thread.sleep(50);
        }
        return false;
    }

    private Integer nullableInt(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        if (row.wasNull()) {
            return null;
        }
        return Integer.valueOf(value);
    }

    private void executeStatement(JdbcTemplate jdbc, String sql) throws SQLException {
        try (var connection = jdbc.getDataSource().getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent PostgreSQL test barrier.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PostgreSQL concurrency test interrupted.", interrupted);
        }
    }

    private record ExistingSuperAdmin(
            UUID id,
            String reason,
            Instant lastModifiedAt,
            Integer lastModifiedBy) {
    }
}
