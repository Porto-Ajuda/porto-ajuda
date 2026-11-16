package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

/**
 * Opt-in evaluation-only poller. It never scans or recovers the durable queue: only an explicitly
 * registered, newly-created job can be claimed. The process-wide provider call ceiling is hard capped at 3.
 */
@Component
@ConditionalOnProperties(value = {
        @ConditionalOnProperty(prefix = "app.ngo-verification.ai-analysis", name = "enabled", havingValue = "true"),
        @ConditionalOnProperty(prefix = "app.ngo-verification.ai-analysis", name = "evaluation-worker-enabled", havingValue = "true")
})
public class VerificacaoAnaliseEvaluationWorker {
    private static final int MAX_PROVIDER_REQUESTS = 3;

    private final VerificacaoAiJobStore jobs;
    private final VerificacaoAnaliseOrchestrator orchestrator;
    private final ObjectProvider<AnaliseRepresentacaoDocumental> providers;
    private final ExecutorService executor;
    private final Instant startedAt = Instant.now();
    private final UUID workerId = UUID.randomUUID();
    private final Set<Long> scopedJobs = ConcurrentHashMap.newKeySet();
    private final AtomicInteger providerCalls = new AtomicInteger();
    private final int requestLimit;

    public VerificacaoAnaliseEvaluationWorker(
            VerificacaoAiJobStore jobs,
            VerificacaoAnaliseOrchestrator orchestrator,
            ObjectProvider<AnaliseRepresentacaoDocumental> providers,
            ExecutorService ngoVerificationAiExecutor,
            @Value("${app.ngo-verification.ai-analysis.evaluation-max-provider-requests:3}") int requestLimit) {
        if (requestLimit < 1 || requestLimit > MAX_PROVIDER_REQUESTS) {
            throw new IllegalArgumentException("Evaluation provider request limit must be between 1 and 3.");
        }
        this.jobs = jobs;
        this.orchestrator = orchestrator;
        this.providers = providers;
        this.executor = ngoVerificationAiExecutor;
        this.requestLimit = requestLimit;
    }

    /** Explicit test/operator hook. It accepts only a just-created, unclaimed current-version job. */
    public synchronized void registerFreshEvaluationJob(long jobId) {
        if (scopedJobs.size() >= 1 && !scopedJobs.contains(jobId)) {
            throw new IllegalStateException("Evaluation scope accepts only one explicitly selected job.");
        }
        VerificacaoAiJobStore.Job candidate = jobs.freshEvaluationCandidate(jobId, startedAt)
                .orElseThrow(() -> new IllegalArgumentException("Job is not a fresh eligible evaluation job."));
        if (!VerificacaoAnaliseOrchestrator.ANALYSIS_PROFILE.equals(candidate.analysisProfile())) {
            throw new IllegalArgumentException("Job profile is outside the evaluation scope.");
        }
        scopedJobs.add(jobId);
    }

    @TransactionalEventListener
    public void registerAfterSubmissionCommit(VerificacaoAiJobEnqueuedEvent event) {
        if (event != null) registerFreshEvaluationJob(event.jobId());
    }

    @Scheduled(fixedDelayString = "${app.ngo-verification.ai-analysis.evaluation-poll-ms:250}")
    public void pollRegisteredJobs() {
        AnaliseRepresentacaoDocumental provider = providers.getIfAvailable();
        if (provider == null || scopedJobs.isEmpty()) return;
        AnaliseRepresentacaoDocumental boundedProvider = request -> {
            int current;
            do {
                current = providerCalls.get();
                if (current >= requestLimit) {
                    throw new AnaliseRepresentacaoDocumental.FalhaProvedor("PROVIDER_QUOTA_EXHAUSTED", false);
                }
            } while (!providerCalls.compareAndSet(current, current + 1));
            return provider.analisar(request);
        };
        for (Long jobId : scopedJobs) {
            VerificacaoAnaliseOrchestrator.ProcessOutcome outcome = orchestrator.processSpecificJob(
                    jobId, boundedProvider, workerId, executor);
            if (outcome != VerificacaoAnaliseOrchestrator.ProcessOutcome.RETRY_SCHEDULED
                    && outcome != VerificacaoAnaliseOrchestrator.ProcessOutcome.IDLE) {
                scopedJobs.remove(jobId);
            }
        }
    }

    public int providerCallsUsed() { return providerCalls.get(); }
    public boolean hasRegisteredJob(long jobId) { return scopedJobs.contains(jobId); }
}
