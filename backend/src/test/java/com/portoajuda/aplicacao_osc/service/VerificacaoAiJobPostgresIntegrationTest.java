package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore;
import com.portoajuda.aplicacao_osc.repository.VerificacaoAnaliseJobRepository;

/** Disposable loopback PostgreSQL checks for the already-applied AI jobs schema. */
@ExtendWith(NgoVerificationTestDatabaseCondition.class)
class VerificacaoAiJobPostgresIntegrationTest {
    @Test
    void concurrentEnqueueForSameSubmissionAndProfileCreatesOneJob() throws Exception {
        Fixture fixture = createSubmittedFixture();
        int workers = 2;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            Future<Integer> first = pool.submit(() -> enqueueAtBarrier(fixture, ready, start));
            Future<Integer> second = pool.submit(() -> enqueueAtBarrier(fixture, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE submission_id="
                + fixture.submissionId() + " AND analysis_profile='REPRESENTATION_V1'"));
    }

    @Test
    void concurrentWorkersSkipAnAlreadyLeasedJob() throws Exception {
        Fixture fixture = createSubmittedFixture();
        long jobId = insertJob(fixture);
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> {
                try (Connection connection = connection()) {
                    connection.setAutoCommit(false);
                    boolean claimed = claim(connection, jobId, UUID.randomUUID());
                    firstClaimed.countDown();
                    assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
                    connection.commit();
                    return claimed;
                }
            });
            assertTrue(firstClaimed.await(10, TimeUnit.SECONDS));
            Future<Boolean> second = pool.submit(() -> {
                try (Connection connection = connection()) {
                    connection.setAutoCommit(false);
                    boolean claimed = claim(connection, jobId, UUID.randomUUID());
                    connection.commit();
                    return claimed;
                }
            });
            assertFalse(second.get(10, TimeUnit.SECONDS), "SKIP LOCKED must not block or duplicate a live claim.");
            releaseFirst.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs "
                + "WHERE id=" + jobId + " AND status='PROCESSING' AND attempts=1"));
    }

    @Test
    void jobIdentityAndAppendOnlyEventsCannotBeRewritten() throws Exception {
        Fixture fixture = createSubmittedFixture();
        long jobId = insertJob(fixture);
        assertThrows(SQLException.class, () -> updateJobApplication(jobId, fixture.applicationId() + 1));
        assertThrows(SQLException.class, () -> updateEvent(jobId));
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs WHERE id=" + jobId));
    }

    @Test
    void exhaustedQuotaMakesJobTerminalForHumanReviewWithoutRetry() throws Exception {
        Fixture fixture = createSubmittedFixture();
        long jobId = insertJob(fixture);
        UUID worker = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-13T12:00:00Z");
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                UPDATE public.ngo_representative_verification_ai_jobs
                   SET status='PROCESSING', attempts=1, lease_owner=?, lease_expires_at=?
                 WHERE id=?
                """)) {
            update.setObject(1, worker);
            update.setObject(2, java.sql.Timestamp.from(now.plusSeconds(60)));
            update.setLong(3, jobId);
            assertEquals(1, update.executeUpdate());
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
        new VerificacaoAnaliseJobRepository(new JdbcTemplate(dataSource)).failPermanently(
                new VerificacaoAiJobStore.Job(jobId, fixture.applicationId(), fixture.submissionId(), 1,
                        "REPRESENTATION_V1", 1, 3), worker, "PROVIDER_QUOTA_EXHAUSTED", now);

        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs "
                + "WHERE id=" + jobId + " AND status='FAILED' AND attempts=1 "
                + "AND last_error_code='PROVIDER_QUOTA_EXHAUSTED' AND completed_at IS NOT NULL"));
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_job_events "
                + "WHERE job_id=" + jobId + " AND event_type='FAILED' "
                + "AND error_code='PROVIDER_QUOTA_EXHAUSTED'"));
    }

    @Test
    void expiredLeasesBecomeRetryOrTerminalFailureAtMaximumAttempts() throws Exception {
        Fixture retryFixture = createSubmittedFixture();
        long retryJobId = insertJob(retryFixture);
        UUID retryOwner = UUID.randomUUID();
        Instant now = Instant.now();
        VerificacaoAnaliseJobRepository repository = new VerificacaoAnaliseJobRepository(new JdbcTemplate(dataSource()));
        VerificacaoAiJobStore.Job retryAttempt = repository.claimById(retryJobId, retryOwner, now,
                Duration.ofMillis(25)).orElseThrow();
        assertEquals(1, retryAttempt.attempt());

        Thread.sleep(60);
        Instant retryRecoveryTime = Instant.now().plusMillis(5);
        assertEquals(1, repository.recoverExpiredLeases(retryRecoveryTime));
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs "
                + "WHERE id=" + retryJobId + " AND status='RETRY' AND attempts=1 AND lease_owner IS NULL "
                + "AND last_error_code='WORKER_LEASE_EXPIRED' AND completed_at IS NULL"));
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_job_events "
                + "WHERE job_id=" + retryJobId + " AND event_type='RETRY_SCHEDULED' "
                + "AND error_code='WORKER_LEASE_EXPIRED'"));

        Fixture exhaustedFixture = createSubmittedFixture();
        long exhaustedJobId = insertJob(exhaustedFixture);
        UUID attemptWorker = UUID.randomUUID();
        Instant attemptOneTime = Instant.now();
        VerificacaoAiJobStore.Job attemptOne = repository.claimById(exhaustedJobId, attemptWorker, attemptOneTime,
                Duration.ofSeconds(90)).orElseThrow();
        repository.fail(attemptOne, attemptWorker, "PROVIDER_TIMEOUT", attemptOneTime.plusMillis(1), attemptOneTime);
        Instant attemptTwoTime = attemptOneTime.plusMillis(5);
        VerificacaoAiJobStore.Job attemptTwo = repository.claimById(exhaustedJobId, attemptWorker,
                attemptTwoTime, Duration.ofSeconds(90)).orElseThrow();
        repository.fail(attemptTwo, attemptWorker, "PROVIDER_TIMEOUT", attemptTwoTime.plusMillis(1), attemptTwoTime);
        Instant attemptThreeTime = attemptTwoTime.plusMillis(5);
        VerificacaoAiJobStore.Job attemptThree = repository.claimById(exhaustedJobId, attemptWorker,
                attemptThreeTime, Duration.ofMillis(25)).orElseThrow();
        assertEquals(3, attemptThree.attempt());
        Thread.sleep(60);
        Instant exhaustedRecoveryTime = Instant.now().plusMillis(5);
        assertEquals(1, repository.recoverExpiredLeases(exhaustedRecoveryTime));
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs "
                + "WHERE id=" + exhaustedJobId + " AND status='FAILED' AND attempts=3 "
                + "AND last_error_code='WORKER_LEASE_EXPIRED' AND completed_at IS NOT NULL"));
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_job_events "
                + "WHERE job_id=" + exhaustedJobId + " AND event_type='FAILED' "
                + "AND error_code='WORKER_LEASE_EXPIRED'"));
    }

    @Test
    void retryIsNotClaimableUntilItsPersistedAvailableAtBoundary() throws Exception {
        Fixture fixture = createSubmittedFixture();
        long jobId = insertJob(fixture);
        VerificacaoAnaliseJobRepository repository = new VerificacaoAnaliseJobRepository(new JdbcTemplate(dataSource()));
        UUID firstWorker = UUID.randomUUID();
        UUID secondWorker = UUID.randomUUID();
        Instant claimNow = Instant.now().plusSeconds(10);
        VerificacaoAiJobStore.Job firstClaim = repository.claimById(jobId, firstWorker, claimNow,
                Duration.ofSeconds(90)).orElseThrow();
        Instant retryAt = claimNow.plusSeconds(30);
        repository.fail(firstClaim, firstWorker, "PROVIDER_TIMEOUT", retryAt, claimNow);

        assertTrue(repository.claimById(jobId, secondWorker, retryAt.minusMillis(1), Duration.ofSeconds(90)).isEmpty());
        VerificacaoAiJobStore.Job retryClaim = repository.claimById(jobId, secondWorker,
                retryAt.plusMillis(1), Duration.ofSeconds(90)).orElseThrow();
        assertEquals(2, retryClaim.attempt());
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs "
                + "WHERE id=" + jobId + " AND status='PROCESSING' AND attempts=2 AND lease_owner='"
                + secondWorker + "'"));
    }

    @Test
    void staleWorkerCannotPersistAfterExpiredLeaseWasRecoveredAndReclaimed() throws Exception {
        Fixture fixture = createSubmittedFixture();
        long jobId = insertJob(fixture);
        VerificacaoAnaliseJobRepository repository = new VerificacaoAnaliseJobRepository(new JdbcTemplate(dataSource()));
        UUID staleWorker = UUID.randomUUID();
        UUID currentWorker = UUID.randomUUID();
        Instant firstClaimNow = Instant.now();
        VerificacaoAiJobStore.Job staleClaim = repository.claimById(jobId, staleWorker, firstClaimNow,
                Duration.ofMillis(25)).orElseThrow();
        Thread.sleep(60);
        // Let the real short test lease expire; the guard trigger does not permit hand-editing PROCESSING lease fields.
        Instant recoveryTime = Instant.now().plusMillis(5);
        assertEquals(1, repository.recoverExpiredLeases(recoveryTime));
        VerificacaoAiJobStore.Job currentClaim = repository.claimById(jobId, currentWorker,
                recoveryTime.plusSeconds(1), Duration.ofSeconds(90)).orElseThrow();
        assertEquals(2, currentClaim.attempt());

        assertThrows(IllegalStateException.class, () -> repository.complete(staleClaim, staleWorker,
                "gemini", "gemini-3.1-flash-lite", 1, 1, "REPRESENTATION_FINDINGS_V2",
                "{\"conclusion\":\"INCONCLUSIVO\"}", "a".repeat(64), recoveryTime.plusSeconds(2)));
        assertEquals(1, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_jobs "
                + "WHERE id=" + jobId + " AND status='PROCESSING' AND attempts=2 AND lease_owner='"
                + currentWorker + "' AND result_summary IS NULL"));
        assertEquals(0, scalarInt("SELECT count(*) FROM public.ngo_representative_verification_ai_job_events "
                + "WHERE job_id=" + jobId + " AND event_type='COMPLETED'"));
    }

    private Fixture createSubmittedFixture() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            int oscId;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO public.oscs(cnpj,status) VALUES (?, 'INATIVA') RETURNING id")) {
                insert.setString(1, "SYNTHETIC-AI-" + UUID.randomUUID());
                try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); oscId = result.getInt(1); }
            }
            long applicationId;
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO public.ngo_representative_verification_applications
                        (osc_id,applicant_user_id,representation_type,status)
                    VALUES (?,1,'LEGAL_REPRESENTATIVE','PENDING') RETURNING id
                    """)) {
                insert.setInt(1, oscId);
                try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); applicationId = result.getLong(1); }
            }
            insertVerificationEvent(connection, applicationId, "APPLICATION_CREATED", null, "PENDING");
            long submissionId;
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO public.ngo_representative_verification_submissions
                        (application_id,version_number,submitted_by_user_id)
                    VALUES (?,1,1) RETURNING id
                    """)) {
                insert.setLong(1, applicationId);
                try (ResultSet result = insert.executeQuery()) { assertTrue(result.next()); submissionId = result.getLong(1); }
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE public.ngo_representative_verification_applications
                       SET current_submission_version=1 WHERE id=?
                    """)) {
                update.setLong(1, applicationId);
                assertEquals(1, update.executeUpdate());
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE public.ngo_representative_verification_submissions
                       SET submitted_at=clock_timestamp() WHERE id=?
                    """)) {
                update.setLong(1, submissionId);
                assertEquals(1, update.executeUpdate());
            }
            insertVerificationEvent(connection, applicationId, "SUBMITTED", "PENDING", "PENDING");
            connection.commit();
            return new Fixture(applicationId, submissionId);
        }
    }

    private long insertJob(Fixture fixture) throws SQLException {
        try (Connection connection = connection(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO public.ngo_representative_verification_ai_jobs
                    (application_id,submission_id,analysis_profile)
                VALUES (?,?,'REPRESENTATION_V1') RETURNING id
                """)) {
            insert.setLong(1, fixture.applicationId());
            insert.setLong(2, fixture.submissionId());
            try (ResultSet result = insert.executeQuery()) {
                assertTrue(result.next());
                long id = result.getLong(1);
                insertJobEvent(connection, id);
                return id;
            }
        }
    }

    private int enqueueAtBarrier(Fixture fixture, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try (Connection connection = connection(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO public.ngo_representative_verification_ai_jobs
                    (application_id,submission_id,analysis_profile)
                VALUES (?,?,'REPRESENTATION_V1') ON CONFLICT (submission_id,analysis_profile) DO NOTHING
                """)) {
            connection.setAutoCommit(false);
            insert.setLong(1, fixture.applicationId());
            insert.setLong(2, fixture.submissionId());
            int inserted = insert.executeUpdate();
            if (inserted == 1) {
                long id;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT id FROM public.ngo_representative_verification_ai_jobs WHERE submission_id=?")) {
                    select.setLong(1, fixture.submissionId());
                    try (ResultSet result = select.executeQuery()) { assertTrue(result.next()); id = result.getLong(1); }
                }
                insertJobEvent(connection, id);
            }
            connection.commit();
            return inserted;
        }
    }

    private boolean claim(Connection connection, long jobId, UUID workerId) throws SQLException {
        try (PreparedStatement claim = connection.prepareStatement("""
                WITH candidate AS (
                    SELECT j.id FROM public.ngo_representative_verification_ai_jobs j
                    JOIN public.ngo_representative_verification_submissions s ON s.id=j.submission_id
                    JOIN public.ngo_representative_verification_applications a ON a.id=j.application_id
                    WHERE j.id=? AND j.status IN ('QUEUED','RETRY') AND j.attempts<j.maximum_attempts
                      AND s.submitted_at IS NOT NULL AND a.current_submission_version=s.version_number
                      AND a.status IN ('PENDING','UNDER_REVIEW')
                    FOR UPDATE OF j SKIP LOCKED
                    LIMIT 1
                )
                UPDATE public.ngo_representative_verification_ai_jobs j
                   SET status='PROCESSING',attempts=j.attempts+1,lease_owner=?,
                       lease_expires_at=clock_timestamp()+interval '90 seconds'
                  FROM candidate c WHERE j.id=c.id
                RETURNING j.id
                """)) {
            claim.setLong(1, jobId);
            claim.setObject(2, workerId);
            try (ResultSet result = claim.executeQuery()) { return result.next(); }
        }
    }

    private void insertJobEvent(Connection connection, long jobId) throws SQLException {
        try (PreparedStatement event = connection.prepareStatement("""
                INSERT INTO public.ngo_representative_verification_ai_job_events(job_id,event_type,attempt)
                VALUES (?,'QUEUED',0)
                """)) {
            event.setLong(1, jobId);
            event.executeUpdate();
        }
    }

    private void insertVerificationEvent(Connection connection, long applicationId, String action,
                                         String from, String to) throws SQLException {
        try (PreparedStatement event = connection.prepareStatement("""
                INSERT INTO public.ngo_representative_verification_events
                    (application_id,actor_user_id,actor_type_snapshot,action,from_status,to_status)
                VALUES (?,1,'APPLICANT',?,?,?)
                """)) {
            event.setLong(1, applicationId);
            event.setString(2, action);
            event.setString(3, from);
            event.setString(4, to);
            event.executeUpdate();
        }
    }

    private void updateJobApplication(long jobId, long applicationId) throws SQLException {
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement(
                "UPDATE public.ngo_representative_verification_ai_jobs SET application_id=? WHERE id=?")) {
            update.setLong(1, applicationId);
            update.setLong(2, jobId);
            update.executeUpdate();
        }
    }

    private void updateEvent(long jobId) throws SQLException {
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                UPDATE public.ngo_representative_verification_ai_job_events SET event_type='FAILED'
                 WHERE job_id=?
                """)) {
            update.setLong(1, jobId);
            update.executeUpdate();
        }
    }

    private int scalarInt(String sql) throws SQLException {
        try (Connection connection = connection(); Statement query = connection.createStatement();
             ResultSet result = query.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(System.getenv("DB_URL"), System.getenv("DB_USER"),
                System.getenv("DB_PASSWORD"));
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("DB_URL"),
                System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
    }

    private record Fixture(long applicationId, long submissionId) { }
}
