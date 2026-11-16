package com.portoajuda.aplicacao_osc.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for bounded, idempotent document-analysis jobs. */
public interface VerificacaoAiJobStore {
    EnqueuedJob enqueue(long applicationId, long submissionId, String profile, Instant now);
    int recoverExpiredLeases(Instant now);
    Optional<Job> claimNext(UUID workerId, Instant now, Duration leaseDuration);
    Optional<Job> claimById(long jobId, UUID workerId, Instant now, Duration leaseDuration);
    Optional<Job> freshEvaluationCandidate(long jobId, Instant notBefore);
    void complete(Job job, UUID workerId, String provider, String model, int inputTokens, int outputTokens,
                  String schemaVersion, String safeResultJson, String resultHash, Instant now);
    void fail(Job job, UUID workerId, String safeErrorCode, Instant retryAt, Instant now);
    void failPermanently(Job job, UUID workerId, String safeErrorCode, Instant now);
    void supersede(Job job, UUID workerId, Instant now);
    Optional<StoredAnalysis> latestForApplicationVersion(long applicationId, int submissionVersion);

    record EnqueuedJob(long id, boolean created) { }
    record Job(long id, long applicationId, long submissionId, int submissionVersion,
               String analysisProfile, int attempt, int maximumAttempts) { }
    record StoredAnalysis(String status, String provider, String model, Integer inputTokens, Integer outputTokens,
                          String errorCode, String resultSummary, Instant completedAt) { }
}
