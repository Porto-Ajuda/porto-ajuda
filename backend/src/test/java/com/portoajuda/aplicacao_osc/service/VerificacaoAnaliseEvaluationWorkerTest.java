package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore;
import com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore.Job;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class VerificacaoAnaliseEvaluationWorkerTest {
    @Test
    void onlyExplicitlyRegisteredFreshCurrentVersionJobIsPolledAndRemovedWhenComplete() {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        VerificacaoAnaliseOrchestrator orchestrator = mock(VerificacaoAnaliseOrchestrator.class);
        @SuppressWarnings("unchecked") ObjectProvider<AnaliseRepresentacaoDocumental> providers = mock(ObjectProvider.class);
        ExecutorService executor = mock(ExecutorService.class);
        AnaliseRepresentacaoDocumental provider = mock(AnaliseRepresentacaoDocumental.class);
        when(providers.getIfAvailable()).thenReturn(provider);
        Job fresh = new Job(301, 101, 201, 1, VerificacaoAnaliseOrchestrator.ANALYSIS_PROFILE, 0, 3);
        when(jobs.freshEvaluationCandidate(eq(301L), any(Instant.class))).thenReturn(Optional.of(fresh));
        when(orchestrator.processSpecificJob(eq(301L), any(), any(), same(executor)))
                .thenReturn(VerificacaoAnaliseOrchestrator.ProcessOutcome.COMPLETED);

        VerificacaoAnaliseEvaluationWorker worker =
                new VerificacaoAnaliseEvaluationWorker(jobs, orchestrator, providers, executor, 3);
        worker.registerFreshEvaluationJob(301);
        worker.pollRegisteredJobs();
        worker.pollRegisteredJobs();

        assertFalse(worker.hasRegisteredJob(301));
        verify(orchestrator, times(1)).processSpecificJob(eq(301L), any(), any(), same(executor));
        verify(jobs, times(1)).freshEvaluationCandidate(eq(301L), any(Instant.class));
        verify(jobs, never()).claimNext(any(), any(), any());
    }

    @Test
    void rejectsHistoricalOrIneligibleIdsAndHardStopsAfterThreeProviderCalls() throws Exception {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        VerificacaoAnaliseOrchestrator orchestrator = mock(VerificacaoAnaliseOrchestrator.class);
        @SuppressWarnings("unchecked") ObjectProvider<AnaliseRepresentacaoDocumental> providers = mock(ObjectProvider.class);
        ExecutorService executor = mock(ExecutorService.class);
        AnaliseRepresentacaoDocumental upstream = mock(AnaliseRepresentacaoDocumental.class);
        when(providers.getIfAvailable()).thenReturn(upstream);
        when(jobs.freshEvaluationCandidate(eq(999L), any(Instant.class))).thenReturn(Optional.empty());
        when(jobs.freshEvaluationCandidate(eq(1000L), any(Instant.class))).thenReturn(Optional.of(
                new Job(1000, 5, 6, 1, "UNEXPECTED_PROFILE", 0, 3)));
        VerificacaoAnaliseEvaluationWorker worker =
                new VerificacaoAnaliseEvaluationWorker(jobs, orchestrator, providers, executor, 3);

        assertThrows(IllegalArgumentException.class, () -> worker.registerFreshEvaluationJob(999));
        assertThrows(IllegalArgumentException.class, () -> worker.registerFreshEvaluationJob(1000));
        when(jobs.freshEvaluationCandidate(eq(301L), any(Instant.class))).thenReturn(Optional.of(
                new Job(301, 101, 201, 1, VerificacaoAnaliseOrchestrator.ANALYSIS_PROFILE, 0, 3)));
        worker.registerFreshEvaluationJob(301);
        when(orchestrator.processSpecificJob(eq(301L), any(), any(), same(executor))).thenAnswer(invocation -> {
            AnaliseRepresentacaoDocumental bounded = invocation.getArgument(1);
            for (int i = 0; i < 3; i++) {
                try { bounded.analisar(request()); } catch (Exception e) { fail(e); }
            }
            assertEquals("PROVIDER_QUOTA_EXHAUSTED", assertThrows(
                    AnaliseRepresentacaoDocumental.FalhaProvedor.class,
                    () -> bounded.analisar(request())).codigo());
            return VerificacaoAnaliseOrchestrator.ProcessOutcome.HUMAN_REVIEW_REQUIRED;
        });

        worker.pollRegisteredJobs();

        assertEquals(3, worker.providerCallsUsed());
        verify(upstream, times(3)).analisar(any());
        verify(jobs, never()).claimNext(any(), any(), any());
        assertFalse(worker.hasRegisteredJob(301));
    }

    @Test
    void restartStartsWithAnEmptyAllowlistAndNeverScansOrRecoversTheGlobalQueue() {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        VerificacaoAnaliseOrchestrator orchestrator = mock(VerificacaoAnaliseOrchestrator.class);
        @SuppressWarnings("unchecked") ObjectProvider<AnaliseRepresentacaoDocumental> providers = mock(ObjectProvider.class);
        ExecutorService executor = mock(ExecutorService.class);
        when(providers.getIfAvailable()).thenReturn(mock(AnaliseRepresentacaoDocumental.class));
        Job fresh = new Job(301, 101, 201, 1, VerificacaoAnaliseOrchestrator.ANALYSIS_PROFILE, 0, 3);
        when(jobs.freshEvaluationCandidate(eq(301L), any(Instant.class))).thenReturn(Optional.of(fresh));
        when(orchestrator.processSpecificJob(eq(301L), any(), any(), same(executor)))
                .thenReturn(VerificacaoAnaliseOrchestrator.ProcessOutcome.RETRY_SCHEDULED);
        VerificacaoAnaliseEvaluationWorker firstProcess =
                new VerificacaoAnaliseEvaluationWorker(jobs, orchestrator, providers, executor, 3);
        firstProcess.registerFreshEvaluationJob(301);
        firstProcess.pollRegisteredJobs();
        assertTrue(firstProcess.hasRegisteredJob(301), "A scheduled retry remains in the explicit in-memory scope.");

        VerificacaoAnaliseEvaluationWorker restartedProcess =
                new VerificacaoAnaliseEvaluationWorker(jobs, orchestrator, providers, executor, 3);
        restartedProcess.pollRegisteredJobs();

        assertFalse(restartedProcess.hasRegisteredJob(301), "Restart does not implicitly adopt pending/historical work.");
        verify(orchestrator, times(1)).processSpecificJob(eq(301L), any(), any(), same(executor));
        verify(jobs, never()).claimNext(any(), any(), any());
        verify(jobs, never()).recoverExpiredLeases(any());
    }

    private AnaliseRepresentacaoDocumental.Solicitacao request() {
        return new AnaliseRepresentacaoDocumental.Solicitacao(
                new AnaliseRepresentacaoDocumental.ContextoAplicacao(
                        "ONG sintética", "00000000000000", "LEGAL_REPRESENTATIVE"),
                List.of(new AnaliseRepresentacaoDocumental.Evidencia(1, "REPRESENTATION_PROOF",
                        "application/pdf", new byte[]{1})), "REPRESENTATION_FINDINGS_V2", 1, 100);
    }
}
