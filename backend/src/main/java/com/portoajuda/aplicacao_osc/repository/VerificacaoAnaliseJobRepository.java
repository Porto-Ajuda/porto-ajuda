package com.portoajuda.aplicacao_osc.repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Private durable AI work queue. All provider-facing state is process-local; this table stores metadata only. */
@Repository
public class VerificacaoAnaliseJobRepository implements VerificacaoAiJobStore {
    private final JdbcTemplate jdbc;

    public VerificacaoAnaliseJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public EnqueuedJob enqueue(long applicationId, long submissionId, String profile, Instant now) {
        List<Long> inserted = jdbc.query("""
                INSERT INTO public.ngo_representative_verification_ai_jobs
                    (application_id, submission_id, analysis_profile, available_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (submission_id, analysis_profile) DO NOTHING
                RETURNING id
                """, (rs, row) -> rs.getLong(1), applicationId, submissionId, profile, Timestamp.from(now));
        long id;
        boolean created = !inserted.isEmpty();
        if (created) {
            id = inserted.getFirst();
            insertEvent(id, "QUEUED", 0, null, null, null, null, now);
        } else {
            Long existing = jdbc.queryForObject("""
                    SELECT id FROM public.ngo_representative_verification_ai_jobs
                     WHERE submission_id=? AND analysis_profile=?
                    """, Long.class, submissionId, profile);
            if (existing == null) throw new IllegalStateException("AI job idempotency lookup failed.");
            id = existing;
        }
        return new EnqueuedJob(id, created);
    }

    @Override
    @Transactional
    public int recoverExpiredLeases(Instant now) {
        List<RecoveredLease> recovered = jdbc.query("""
                UPDATE public.ngo_representative_verification_ai_jobs
                   SET status=CASE WHEN attempts >= maximum_attempts THEN 'FAILED' ELSE 'RETRY' END,
                       available_at=?, lease_owner=NULL, lease_expires_at=NULL,
                       last_error_code='WORKER_LEASE_EXPIRED',
                       completed_at=CASE WHEN attempts >= maximum_attempts
                           THEN CAST(? AS TIMESTAMPTZ) ELSE NULL::TIMESTAMPTZ END,
                       updated_at=?
                 WHERE status='PROCESSING' AND lease_expires_at <= ?
                 RETURNING id, attempts, status, provider, model
                """, (rs, row) -> new RecoveredLease(rs.getLong("id"), rs.getInt("attempts"),
                rs.getString("status"), rs.getString("provider"), rs.getString("model")),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        for (RecoveredLease job : recovered) {
            insertEvent(job.id(), "FAILED".equals(job.status()) ? "FAILED" : "RETRY_SCHEDULED",
                    job.attempts(), job.provider(), job.model(), "WORKER_LEASE_EXPIRED", null, now);
        }
        return recovered.size();
    }

    @Override
    @Transactional
    public Optional<Job> claimNext(UUID workerId, Instant now, Duration leaseDuration) {
        return claimEligible(null, workerId, now, leaseDuration);
    }

    @Override
    @Transactional
    public Optional<Job> claimById(long jobId, UUID workerId, Instant now, Duration leaseDuration) {
        if (jobId < 1) return Optional.empty();
        return claimEligible(jobId, workerId, now, leaseDuration);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Job> freshEvaluationCandidate(long jobId, Instant notBefore) {
        if (jobId < 1 || notBefore == null) return Optional.empty();
        return jdbc.query("""
                SELECT j.id,j.application_id,j.submission_id,s.version_number,j.analysis_profile,
                       j.attempts,j.maximum_attempts
                  FROM public.ngo_representative_verification_ai_jobs j
                  JOIN public.ngo_representative_verification_submissions s ON s.id=j.submission_id
                  JOIN public.ngo_representative_verification_applications a ON a.id=j.application_id
                 WHERE j.id=? AND j.created_at >= ? AND j.status='QUEUED' AND j.attempts=0
                   AND j.lease_owner IS NULL AND j.lease_expires_at IS NULL
                   AND s.submitted_at IS NOT NULL AND a.current_submission_version=s.version_number
                   AND a.status IN ('PENDING','UNDER_REVIEW')
                """, (rs, row) -> new Job(rs.getLong("id"), rs.getLong("application_id"),
                rs.getLong("submission_id"), rs.getInt("version_number"), rs.getString("analysis_profile"),
                rs.getInt("attempts"), rs.getInt("maximum_attempts")), jobId, Timestamp.from(notBefore))
                .stream().findFirst();
    }

    private Optional<Job> claimEligible(Long requestedJobId, UUID workerId, Instant now, Duration leaseDuration) {
        List<Job> claimed = jdbc.query("""
                WITH candidate AS (
                    SELECT j.id
                      FROM public.ngo_representative_verification_ai_jobs j
                      JOIN public.ngo_representative_verification_submissions s ON s.id=j.submission_id
                      JOIN public.ngo_representative_verification_applications a ON a.id=j.application_id
                     WHERE (CAST(? AS BIGINT) IS NULL OR j.id=CAST(? AS BIGINT))
                       AND j.status IN ('QUEUED','RETRY') AND j.available_at <= ?
                       AND j.attempts < j.maximum_attempts
                       AND s.submitted_at IS NOT NULL
                       AND a.current_submission_version=s.version_number
                       AND a.status IN ('PENDING','UNDER_REVIEW')
                     ORDER BY j.available_at, j.created_at, j.id
                     FOR UPDATE OF j SKIP LOCKED
                     LIMIT 1
                ), claimed AS (
                    UPDATE public.ngo_representative_verification_ai_jobs j
                       SET status='PROCESSING', attempts=j.attempts+1,
                           lease_owner=?, lease_expires_at=?, updated_at=?
                      FROM candidate c WHERE j.id=c.id
                    RETURNING j.id,j.application_id,j.submission_id,j.analysis_profile,j.attempts,j.maximum_attempts
                )
                SELECT c.id,c.application_id,c.submission_id,c.analysis_profile,c.attempts,c.maximum_attempts,
                       s.version_number
                  FROM claimed c JOIN public.ngo_representative_verification_submissions s ON s.id=c.submission_id
                """, (rs, row) -> new Job(rs.getLong("id"), rs.getLong("application_id"),
                rs.getLong("submission_id"), rs.getInt("version_number"), rs.getString("analysis_profile"),
                rs.getInt("attempts"), rs.getInt("maximum_attempts")), requestedJobId, requestedJobId,
                Timestamp.from(now), workerId,
                Timestamp.from(now.plus(leaseDuration)), Timestamp.from(now));
        if (claimed.isEmpty()) return Optional.empty();
        Job job = claimed.getFirst();
        insertEvent(job.id(), "CLAIMED", job.attempt(), null, null, null, null, now);
        return Optional.of(job);
    }

    @Override
    @Transactional
    public void complete(Job job, UUID workerId, String provider, String model, int inputTokens, int outputTokens,
                         String schemaVersion, String resultJson, String sha256, Instant now) {
        int updated = jdbc.update("""
                UPDATE public.ngo_representative_verification_ai_jobs
                   SET status='COMPLETED', lease_owner=NULL, lease_expires_at=NULL,
                       provider=?, model=?, input_tokens=?, output_tokens=?, result_schema_version=?,
                       result_summary=?::jsonb, result_sha256=?, last_error_code=NULL,
                       completed_at=?, updated_at=?
                 WHERE id=? AND status='PROCESSING' AND lease_owner=?
                """, provider, model, inputTokens, outputTokens, schemaVersion, resultJson, sha256,
                Timestamp.from(now), Timestamp.from(now), job.id(), workerId);
        requireOwned(updated);
        insertEvent(job.id(), "COMPLETED", job.attempt(), provider, model, null, sha256, now);
    }

    @Override
    @Transactional
    public void fail(Job job, UUID workerId, String safeErrorCode, Instant retryAt, Instant now) {
        boolean terminal = job.attempt() >= job.maximumAttempts();
        String nextStatus = terminal ? "FAILED" : "RETRY";
        int updated = jdbc.update("""
                UPDATE public.ngo_representative_verification_ai_jobs
                   SET status=?, available_at=?, lease_owner=NULL, lease_expires_at=NULL,
                       last_error_code=?, completed_at=?, updated_at=?
                 WHERE id=? AND status='PROCESSING' AND lease_owner=?
                """, nextStatus, Timestamp.from(terminal ? now : retryAt), safeErrorCode,
                terminal ? Timestamp.from(now) : null, Timestamp.from(now), job.id(), workerId);
        requireOwned(updated);
        insertEvent(job.id(), terminal ? "FAILED" : "RETRY_SCHEDULED", job.attempt(), null, null,
                safeErrorCode, null, now);
    }

    @Override
    @Transactional
    public void failPermanently(Job job, UUID workerId, String safeErrorCode, Instant now) {
        int updated = jdbc.update("""
                UPDATE public.ngo_representative_verification_ai_jobs
                   SET status='FAILED', available_at=?, lease_owner=NULL, lease_expires_at=NULL,
                       last_error_code=?, completed_at=?, updated_at=?
                 WHERE id=? AND status='PROCESSING' AND lease_owner=?
                """, Timestamp.from(now), safeErrorCode, Timestamp.from(now), Timestamp.from(now),
                job.id(), workerId);
        requireOwned(updated);
        insertEvent(job.id(), "FAILED", job.attempt(), null, null, safeErrorCode, null, now);
    }

    @Override
    @Transactional
    public void supersede(Job job, UUID workerId, Instant now) {
        int updated = jdbc.update("""
                UPDATE public.ngo_representative_verification_ai_jobs
                   SET status='SUPERSEDED', lease_owner=NULL, lease_expires_at=NULL,
                       completed_at=?, updated_at=?
                 WHERE id=? AND status='PROCESSING' AND lease_owner=?
                """, Timestamp.from(now), Timestamp.from(now), job.id(), workerId);
        requireOwned(updated);
        insertEvent(job.id(), "SUPERSEDED", job.attempt(), null, null, null, null, now);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredAnalysis> latestForApplicationVersion(long applicationId, int submissionVersion) {
        return jdbc.query("""
                SELECT j.status,j.provider,j.model,j.input_tokens,j.output_tokens,j.last_error_code,
                       j.result_summary::text AS result_summary,j.completed_at
                  FROM public.ngo_representative_verification_ai_jobs j
                  JOIN public.ngo_representative_verification_submissions s ON s.id=j.submission_id
                 WHERE j.application_id=? AND s.version_number=?
                 ORDER BY j.created_at DESC,j.id DESC
                 LIMIT 1
                """, (rs, row) -> new StoredAnalysis(rs.getString("status"), rs.getString("provider"),
                rs.getString("model"), (Integer) rs.getObject("input_tokens"),
                (Integer) rs.getObject("output_tokens"), rs.getString("last_error_code"),
                rs.getString("result_summary"), rs.getTimestamp("completed_at") == null ? null
                        : rs.getTimestamp("completed_at").toInstant()), applicationId, submissionVersion)
                .stream().findFirst();
    }

    private void insertEvent(long jobId, String event, int attempt, String provider, String model,
                             String errorCode, String resultHash, Instant now) {
        jdbc.update("""
                INSERT INTO public.ngo_representative_verification_ai_job_events
                    (job_id,event_type,attempt,provider,model,error_code,result_sha256,created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, jobId, event, attempt, provider, model, errorCode, resultHash, Timestamp.from(now));
    }

    private void requireOwned(int updated) {
        if (updated != 1) throw new IllegalStateException("AI job lease is no longer owned by this worker.");
    }

    private record RecoveredLease(long id, int attempts, String status, String provider, String model) { }
}
