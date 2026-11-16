package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Real PostgreSQL tests. They only run against the exact, explicitly confirmed,
 * task-created disposable database; fixtures/history are discarded with that cluster.
 * Apply the repository baseline and the NOT-YET-APPLIED migration before running.
 */
@ExtendWith(NgoVerificationTestDatabaseCondition.class)
class VerificacaoRepresentantePostgresIntegrationTest {
    private static PGSimpleDataSource dataSource;

    @BeforeAll
    static void connectOnlyToConfirmedDisposableDatabase() throws Exception {
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(System.getenv("DB_URL"));
        dataSource.setUser(System.getenv("DB_USER"));
        dataSource.setPassword(System.getenv("DB_PASSWORD"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT current_database(), inet_server_addr(), inet_server_port(), current_user")) {
                assertTrue(result.next());
                assertEquals("ngo_representative_verification_test", result.getString(1));
                assertEquals("127.0.0.1", result.getString(2));
                assertEquals(55447, result.getInt(3));
                assertEquals("ngo_verification_runtime", result.getString(4));
            }
            assertTrue(tableExists(connection, "ngo_representative_verification_applications"),
                    "Apply the migration to the disposable database before this test.");
        }
    }

    @Test
    void migrationObjectsRlsAndRuntimeFunctionPrivilegesAreEnforced() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("""
                    SELECT c.relrowsecurity,
                           has_table_privilege('anon', 'public.ngo_representative_verification_applications', 'SELECT'),
                           has_table_privilege('authenticated', 'public.ngo_representative_verification_events', 'INSERT'),
                           has_function_privilege(current_user, 'public.ngo_representative_is_publicly_eligible(integer)', 'EXECUTE'),
                           has_function_privilege('anon', 'public.ngo_representative_is_publicly_eligible(integer)', 'EXECUTE')
                      FROM pg_class c WHERE c.oid='public.ngo_representative_verification_applications'::regclass
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
                assertFalse(result.getBoolean(2));
                assertFalse(result.getBoolean(3));
                assertTrue(result.getBoolean(4), "Runtime database owner must call the eligibility function.");
                assertFalse(result.getBoolean(5));
            }
            assertEquals(0, scalarInt(connection, """
                    SELECT count(*) FROM pg_policy p
                     WHERE p.polrelid='storage.objects'::regclass
                       AND (0=ANY(p.polroles) OR EXISTS (
                           SELECT 1 FROM unnest(p.polroles) role_id JOIN pg_roles r ON r.oid=role_id
                           WHERE r.rolname IN ('anon','authenticated')))
                    """));
            assertTrue(scalarBoolean(connection, """
                    SELECT relrowsecurity FROM pg_class WHERE oid='storage.objects'::regclass
                    """));
            assertTrue(scalarBoolean(connection, "SELECT rolbypassrls FROM pg_roles WHERE rolname='service_role'"));
            assertTrue(scalarBoolean(connection, "SELECT rolbypassrls FROM pg_roles WHERE rolname=current_user"),
                    "The synthetic runtime role must match the confirmed TEST role attributes.");
            assertFalse(scalarBoolean(connection, "SELECT rolbypassrls FROM pg_roles WHERE rolname='anon'"));
            assertFalse(scalarBoolean(connection, "SELECT rolbypassrls FROM pg_roles WHERE rolname='authenticated'"));
            assertTrue(scalarBoolean(connection, """
                    SELECT pg_get_userbyid(c.relowner)='supabase_storage_admin'
                           AND NOT pg_has_role(current_user,c.relowner,'MEMBER')
                      FROM pg_class c WHERE c.oid='storage.objects'::regclass
                    """), "The migration runtime is intentionally not the Storage table owner.");
            assertTrue(scalarBoolean(connection, """
                    SELECT EXISTS (SELECT 1 FROM storage.buckets WHERE id='ngo-representative-evidence'
                      AND public IS FALSE AND file_size_limit=10485760
                      AND allowed_mime_types @> ARRAY['application/pdf','image/jpeg','image/png']::text[])
                    """));
            assertTrue(scalarBoolean(connection, """
                    SELECT i.indisunique AND pg_get_expr(i.indpred, i.indrelid) LIKE '%PENDING%'
                      FROM pg_index i WHERE i.indexrelid='public.uq_ngo_verification_one_active_application'::regclass
                    """));
        }
    }

    @Test
    void legacyPendingIsNotHiddenAndNewNgoRequiresApprovalWithoutChangingOperationalState() throws Exception {
        int legacyId = scalarInt("SELECT id FROM public.oscs WHERE cnpj='TESTE-SINTETICO-LEGADO-001'");
        long legacyApplication = createInitialApplication(legacyId, "LEGAL_REPRESENTATIVE");
        assertTrue(isVerificationEligible(legacyId), "Legacy exemption must survive voluntary pending verification.");
        assertEquals("ATIVA", scalarString("SELECT status FROM public.oscs WHERE id=" + legacyId));

        int newNgo = createNgo("INATIVA", true);
        assertFalse(isVerificationEligible(newNgo));
        long newApplication = createInitialApplication(newNgo, "AUTHORIZED_DELEGATE");
        assertFalse(isVerificationEligible(newNgo));
        approve(newApplication);
        assertTrue(isVerificationEligible(newNgo));
        assertEquals("INATIVA", scalarString("SELECT status FROM public.oscs WHERE id=" + newNgo));
        assertTrue(scalarBoolean("SELECT data_arquivamento IS NOT NULL FROM public.oscs WHERE id=" + newNgo));
    }

    @Test
    void rejectedCycleRemainsImmutableAndAllowsNewLinkedCycle() throws Exception {
        int ngo = createNgo("ATIVA", false);
        long rejected = createRejectedApplication(ngo);
        long next = createReapplication(ngo, rejected, 2);
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("REJECTED", scalarString(connection,
                    "SELECT status FROM public.ngo_representative_verification_applications WHERE id=" + rejected));
            assertEquals(2, scalarInt(connection,
                    "SELECT count(*) FROM public.ngo_representative_verification_applications WHERE osc_id=" + ngo));
            assertEquals(rejected, scalarLong(connection,
                    "SELECT parent_application_id FROM public.ngo_representative_verification_applications WHERE id=" + next));
            assertEquals(3, scalarInt(connection,
                    "SELECT count(*) FROM public.ngo_representative_verification_events WHERE application_id=" + rejected));
            assertEquals(1, scalarInt(connection,
                    "SELECT count(*) FROM public.ngo_representative_verification_events WHERE application_id=" + next
                            + " AND action='REAPPLICATION_CREATED'"));
        }
    }

    @Test
    void concurrentReapplicationsCannotCreateTwoActiveRows() throws Exception {
        int ngo = createNgo("ATIVA", false);
        long rejected = createRejectedApplication(ngo);
        int callers = 2;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            Future<Boolean> first = pool.submit(() -> attemptReapplication(ngo, rejected, ready, start));
            Future<Boolean> second = pool.submit(() -> attemptReapplication(ngo, rejected, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            pool.shutdownNow();
        }
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(2, scalarInt(connection, "SELECT count(*) FROM public.ngo_representative_verification_applications WHERE osc_id=" + ngo));
            assertEquals(1, scalarInt(connection, """
                    SELECT count(*) FROM public.ngo_representative_verification_applications
                     WHERE osc_id=%d AND status IN ('PENDING','UNDER_REVIEW','INFORMATION_REQUESTED')
                    """.formatted(ngo)));
        }
    }

    @Test
    void concurrentReviewerDecisionsProduceOnlyOneFinalOutcome() throws Exception {
        int ngo = createNgo("ATIVA", false);
        long application = createUnderReviewApplication(ngo);
        int callers = 2;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            Future<Boolean> approval = pool.submit(() -> decideAtBarrier(application, "APPROVED", "APPROVED", ready, start));
            Future<Boolean> rejection = pool.submit(() -> decideAtBarrier(application, "REJECTED", "REJECTED", ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (approval.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (rejection.get(20, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            pool.shutdownNow();
        }
        assertTrue(List.of("APPROVED", "REJECTED").contains(scalarString(
                "SELECT status FROM public.ngo_representative_verification_applications WHERE id=" + application)));
        assertEquals(1, scalarInt("""
                SELECT count(*) FROM public.ngo_representative_verification_events
                 WHERE application_id=%d AND action IN ('APPROVED','REJECTED')
                """.formatted(application)));
    }

    @Test
    void concurrentSubmissionOfOneDraftCreatesOneSubmissionEvent() throws Exception {
        int ngo = createNgo("ATIVA", false);
        long application = createInitialApplication(ngo, "LEGAL_REPRESENTATIVE");
        long submissionId;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO public.ngo_representative_verification_submissions
                        (application_id,version_number,submitted_by_user_id)
                    VALUES (?,1,1) RETURNING id
                    """)) {
                insert.setLong(1, application);
                try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); submissionId = result.getLong(1); }
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE public.ngo_representative_verification_applications
                       SET current_submission_version=1 WHERE id=?
                    """)) {
                update.setLong(1, application);
                assertEquals(1, update.executeUpdate());
            }
            connection.commit();
        }

        int callers = 2;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            Future<Boolean> first = pool.submit(() -> submitDraftAtBarrier(application, submissionId, ready, start));
            Future<Boolean> second = pool.submit(() -> submitDraftAtBarrier(application, submissionId, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
        } finally { pool.shutdownNow(); }
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_submissions WHERE id="
                + submissionId + " AND submitted_at IS NOT NULL"));
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_events WHERE application_id="
                + application + " AND action='SUBMITTED'"));
    }

    @Test
    void concurrentDuplicateCnpjIsRejectedByTheDatabaseUniqueIndex() throws Exception {
        String cnpj = "SYNTHETIC-DUPLICATE-" + UUID.randomUUID();
        int callers = 2;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            Future<Boolean> first = pool.submit(() -> insertDuplicateCnpj(cnpj, ready, start));
            Future<Boolean> second = pool.submit(() -> insertDuplicateCnpj(cnpj, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            pool.shutdownNow();
        }
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(1, scalarInt(connection, "SELECT count(*) FROM public.oscs WHERE cnpj='" + cnpj + "'"));
        }
    }

    @Test
    void rejectedApplicationReopeningRequiresAuditedIndependentReviewerAndCycleLimitIsPreserved() throws Exception {
        int ngo = createNgo("ATIVA", false);
        long rejected = createRejectedApplication(ngo);
        try (Connection connection = dataSource.getConnection()) {
            SQLException transition = assertThrows(SQLException.class, () -> {
                connection.setAutoCommit(false);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE public.ngo_representative_verification_applications SET status='UNDER_REVIEW' WHERE id=?")) {
                    update.setLong(1, rejected);
                    update.executeUpdate();
                }
                connection.commit();
            });
            assertNotNull(transition.getSQLState());
            connection.rollback();
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            insertEvent(connection, rejected, 2, "ADMIN_REOPENED", "REJECTED", "INFORMATION_REQUESTED",
                    "Justificativa administrativa sintética.");
            setStatus(connection, rejected, "INFORMATION_REQUESTED");
            connection.commit();
        }
        assertEquals("INFORMATION_REQUESTED", scalarString(
                "SELECT status FROM public.ngo_representative_verification_applications WHERE id=" + rejected));
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_events WHERE application_id="
                + rejected + " AND action='REJECTED'"));
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_events WHERE application_id="
                + rejected + " AND action='ADMIN_REOPENED'"));
        try (Connection connection = dataSource.getConnection(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO public.ngo_representative_verification_applications
                    (osc_id,application_number,parent_application_id,applicant_user_id,representation_type)
                VALUES (?,4,?,1,'LEGAL_REPRESENTATIVE')
                """)) {
            insert.setInt(1, ngo);
            insert.setLong(2, rejected);
            assertThrows(SQLException.class, insert::executeUpdate);
        }
        assertEquals("INFORMATION_REQUESTED", scalarString(
                "SELECT status FROM public.ngo_representative_verification_applications WHERE id=" + rejected));
    }

    @Test
    void sixMonthAccountDeletionCanRemoveSyntheticUserWithoutDeletingVerificationHistory() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            int userId;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO public.usuarios DEFAULT VALUES RETURNING id")) {
                try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); userId = result.getInt(1); }
            }
            int ngo;
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO public.oscs(cnpj,status) VALUES (?, 'ATIVA') RETURNING id
                    """)) {
                insert.setString(1, "SYNTHETIC-ACCOUNT-DELETE-" + UUID.randomUUID());
                try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); ngo = result.getInt(1); }
            }
            long application;
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO public.ngo_representative_verification_applications
                        (osc_id,applicant_user_id,representation_type,status,current_submission_version)
                    VALUES (?,?,'LEGAL_REPRESENTATIVE','PENDING',1) RETURNING id
                    """)) {
                insert.setInt(1, ngo);
                insert.setInt(2, userId);
                try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); application = result.getLong(1); }
            }
            insertEvent(connection, application, userId, "APPLICATION_CREATED", null, "PENDING", null);
            long submission;
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO public.ngo_representative_verification_submissions
                        (application_id,version_number,submitted_by_user_id,submitted_at)
                    VALUES (?,1,?,clock_timestamp()) RETURNING id
                    """)) {
                insert.setLong(1, application);
                insert.setInt(2, userId);
                try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); submission = result.getLong(1); }
            }
            long evidence;
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO public.ngo_representative_verification_evidence
                        (submission_id,document_type,storage_key,media_type,size_bytes,uploaded_by_user_id)
                    VALUES (?,'REPRESENTATION_PROOF',?,'image/png',16,?) RETURNING id
                    """)) {
                insert.setLong(1, submission);
                insert.setString(2, "applications/synthetic/account-delete.png");
                insert.setInt(3, userId);
                try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); evidence = result.getLong(1); }
            }
            insertEvent(connection, application, userId, "REVIEW_STARTED", "PENDING", "UNDER_REVIEW", null);
            setStatus(connection, application, "UNDER_REVIEW");
            insertEvent(connection, application, userId, "APPROVED", "UNDER_REVIEW", "APPROVED",
                    "Decisão sintética mantida para auditoria.");
            setStatus(connection, application, "APPROVED");
            try (PreparedStatement approve = connection.prepareStatement(
                    "SELECT public.approve_ngo_verification_retention_policy(?,?,?)")) {
                approve.setInt(1, userId);
                approve.setString(2, "TEST-SYNTHETIC-RETENTION");
                approve.setString(3, "Aprovação temporária dentro da transação sintética do teste.");
                approve.execute();
            }

            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM public.usuarios WHERE id=?")) {
                delete.setInt(1, userId);
                assertEquals(1, delete.executeUpdate(), "The six-month cleanup deletion must not be blocked by verification FKs.");
            }
            try (Statement checkDeferred = connection.createStatement()) {
                checkDeferred.execute("SET CONSTRAINTS ALL IMMEDIATE");
            }
            assertEquals(1, scalarInt(connection, "SELECT count(*) FROM public.ngo_representative_verification_applications WHERE id="
                    + application + " AND applicant_user_id IS NULL AND status='APPROVED'"));
            assertEquals(1, scalarInt(connection, "SELECT count(*) FROM public.ngo_representative_verification_submissions WHERE application_id="
                    + application + " AND submitted_by_user_id IS NULL AND submitted_at IS NOT NULL"));
            assertEquals(1, scalarInt(connection, "SELECT count(*) FROM public.ngo_representative_verification_evidence WHERE id="
                    + evidence + " AND uploaded_by_user_id IS NULL AND disposal_status='ACTIVE'"));
            assertEquals(1, scalarInt(connection, "SELECT count(*) FROM public.ngo_representative_verification_events WHERE application_id="
                    + application + " AND action='APPROVED' AND actor_user_id IS NULL AND justification='Decisão sintética mantida para auditoria.'"));
            assertEquals(1, scalarInt(connection, "SELECT count(*) FROM public.ngo_representative_verification_retention_approval_events WHERE approval_reference='TEST-SYNTHETIC-RETENTION' AND actor_user_id IS NULL"));
            assertTrue(scalarBoolean(connection, "SELECT retention_policy_status='APPROVED' AND retention_policy_approved_by_user_id IS NULL FROM public.ngo_representative_verification_config WHERE id=1"));
            connection.rollback();
        }
    }

    private static boolean attemptReapplication(int ngo, long parent, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long id = insertApplication(connection, ngo, 2, parent, "AUTHORIZED_DELEGATE");
                insertEvent(connection, id, "REAPPLICATION_CREATED", null, "PENDING", null);
                connection.commit();
                return true;
            } catch (SQLException conflict) {
                connection.rollback();
                if ("23505".equals(conflict.getSQLState())) return false;
                throw conflict;
            }
        }
    }

    private static boolean decideAtBarrier(long application, String status, String action,
                                            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            String current;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT status FROM public.ngo_representative_verification_applications WHERE id=? FOR UPDATE")) {
                select.setLong(1, application);
                try (ResultSet result = select.executeQuery()) { assertTrue(result.next()); current = result.getString(1); }
            }
            if (!"UNDER_REVIEW".equals(current)) { connection.rollback(); return false; }
            insertEvent(connection, application, action, "UNDER_REVIEW", status, "Decisão concorrente sintética.");
            setStatus(connection, application, status);
            connection.commit();
            return true;
        }
    }

    private static boolean submitDraftAtBarrier(long application, long submissionId,
                                                CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE public.ngo_representative_verification_submissions
                       SET submitted_at=clock_timestamp()
                     WHERE id=? AND application_id=? AND submitted_at IS NULL
                    RETURNING submitted_at
                    """)) {
                update.setLong(1, submissionId);
                update.setLong(2, application);
                try (ResultSet result = update.executeQuery()) {
                    if (!result.next()) { connection.rollback(); return false; }
                }
            }
            insertEvent(connection, application, "SUBMITTED", "PENDING", "PENDING", null);
            connection.commit();
            return true;
        }
    }

    private static boolean insertDuplicateCnpj(String cnpj, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO public.oscs (cnpj,status) VALUES (?,'ATIVA')")) {
                insert.setString(1, cnpj);
                insert.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException conflict) {
                connection.rollback();
                if ("23505".equals(conflict.getSQLState())) return false;
                throw conflict;
            }
        }
    }

    private static long createInitialApplication(int ngo, String representation) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long id = insertApplication(connection, ngo, 1, null, representation);
            insertEvent(connection, id, "APPLICATION_CREATED", null, "PENDING", null);
            connection.commit();
            return id;
        }
    }

    private static long createRejectedApplication(int ngo) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long id = insertApplication(connection, ngo, 1, null, "LEGAL_REPRESENTATIVE");
            insertEvent(connection, id, "APPLICATION_CREATED", null, "PENDING", null);
            insertEvent(connection, id, "REVIEW_STARTED", "PENDING", "UNDER_REVIEW", null);
            setStatus(connection, id, "UNDER_REVIEW");
            insertEvent(connection, id, "REJECTED", "UNDER_REVIEW", "REJECTED", "Documento sintético insuficiente.");
            setStatus(connection, id, "REJECTED");
            connection.commit();
            return id;
        }
    }

    private static long createUnderReviewApplication(int ngo) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long id = insertApplication(connection, ngo, 1, null, "LEGAL_REPRESENTATIVE");
            insertEvent(connection, id, "APPLICATION_CREATED", null, "PENDING", null);
            insertEvent(connection, id, "REVIEW_STARTED", "PENDING", "UNDER_REVIEW", null);
            setStatus(connection, id, "UNDER_REVIEW");
            connection.commit();
            return id;
        }
    }

    private static void approve(long application) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            insertEvent(connection, application, "REVIEW_STARTED", "PENDING", "UNDER_REVIEW", null);
            setStatus(connection, application, "UNDER_REVIEW");
            insertEvent(connection, application, "APPROVED", "UNDER_REVIEW", "APPROVED",
                    "Conferência sintética aprovada.");
            setStatus(connection, application, "APPROVED");
            connection.commit();
        }
    }

    private static long createReapplication(int ngo, long parent, int cycle) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long id = insertApplication(connection, ngo, cycle, parent, "AUTHORIZED_DELEGATE");
            insertEvent(connection, id, "REAPPLICATION_CREATED", null, "PENDING", null);
            connection.commit();
            return id;
        }
    }

    private static long insertApplication(Connection connection, int ngo, int cycle, Long parent, String representation) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO public.ngo_representative_verification_applications
                    (osc_id,application_number,parent_application_id,applicant_user_id,representation_type)
                VALUES (?,?,?,?,?) RETURNING id
                """)) {
            insert.setInt(1, ngo);
            insert.setInt(2, cycle);
            if (parent == null) insert.setNull(3, java.sql.Types.BIGINT); else insert.setLong(3, parent);
            insert.setInt(4, 1);
            insert.setString(5, representation);
            try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); return result.getLong(1); }
        }
    }

    private static void insertEvent(Connection connection, long application, String action,
                                    String from, String to, String justification) throws SQLException {
        insertEvent(connection, application, 1, action, from, to, justification);
    }

    private static void insertEvent(Connection connection, long application, int actorId, String action,
                                    String from, String to, String justification) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO public.ngo_representative_verification_events
                    (application_id,actor_user_id,actor_type_snapshot,action,from_status,to_status,justification)
                VALUES (?,?,?,?,?,?,?)
                """)) {
            insert.setLong(1, application);
            insert.setInt(2, actorId);
            insert.setString(3, List.of("APPLICATION_CREATED", "REAPPLICATION_CREATED", "SUBMITTED", "RESUBMITTED")
                    .contains(action) ? "APPLICANT" : "ADMINISTRATOR");
            insert.setString(4, action);
            insert.setString(5, from);
            insert.setString(6, to);
            insert.setString(7, justification);
            insert.executeUpdate();
        }
    }

    private static void setStatus(Connection connection, long application, String status) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE public.ngo_representative_verification_applications SET status=? WHERE id=?")) {
            update.setString(1, status);
            update.setLong(2, application);
            assertEquals(1, update.executeUpdate());
        }
    }

    private static int createNgo(String status, boolean archived) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO public.oscs (cnpj,status,data_arquivamento) VALUES (?,?,CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END) RETURNING id
                """)) {
            insert.setString(1, "SYNTHETIC-" + UUID.randomUUID());
            insert.setString(2, status);
            insert.setBoolean(3, archived);
            try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); return result.getInt(1); }
        }
    }

    private static boolean isVerificationEligible(int ngo) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT public.ngo_representative_is_publicly_eligible(?)")) {
            query.setInt(1, ngo);
            try (ResultSet result = query.executeQuery()) { assertTrue(result.next()); return result.getBoolean(1); }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT to_regclass('public.' || ?) IS NOT NULL")) {
            query.setString(1, table);
            try (ResultSet result = query.executeQuery()) { result.next(); return result.getBoolean(1); }
        }
    }

    private static int scalarInt(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection()) { return scalarInt(connection, sql); }
    }

    private static int scalarInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next()); return result.getInt(1);
        }
    }

    private static long scalarLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next()); return result.getLong(1);
        }
    }

    private static String scalarString(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection()) { return scalarString(connection, sql); }
    }

    private static String scalarString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next()); return result.getString(1);
        }
    }

    private static boolean scalarBoolean(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection()) { return scalarBoolean(connection, sql); }
    }

    private static boolean scalarBoolean(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next()); return result.getBoolean(1);
        }
    }
}
