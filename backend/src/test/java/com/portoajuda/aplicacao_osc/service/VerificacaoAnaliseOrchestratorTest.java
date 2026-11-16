package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore;
import com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore.Job;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.AnalysisEvidenceRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.ApplicationRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.SubmissionRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VerificacaoAnaliseOrchestratorTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final byte[] SYNTHETIC_PDF = "%PDF-1.4 synthetic-only".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final UUID WORKER = UUID.fromString("6f22901c-21d7-4e1b-a23d-90df55559a7a");

    @Test
    void disabledModeDoesNotTouchJobStoreAndCannotQueueDocuments() {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        VerificacaoAnaliseOrchestrator service = orchestrator(jobs, mock(VerificacaoRepresentanteJdbcRepository.class),
                mock(PrivateVerificationEvidenceStorage.class), false, Duration.ofSeconds(1));

        assertNull(service.enqueueAfterSubmission(80, new SubmissionRow(1, 80, 1, NOW)));
        assertEquals(VerificacaoAnaliseOrchestrator.ProcessOutcome.DISABLED,
                service.processNext(mock(AnaliseRepresentacaoDocumental.class), WORKER, Executors.newSingleThreadExecutor()));
        verifyNoInteractions(jobs);
    }

    @Test
    void registeredNameMismatchRemainsInconclusiveRatherThanBeingTreatedAsFraud() {
        VerificacaoAnaliseOrchestrator service = orchestrator(mock(VerificacaoAiJobStore.class),
                mock(VerificacaoRepresentanteJdbcRepository.class), mock(PrivateVerificationEvidenceStorage.class),
                true, Duration.ofSeconds(1));
        var modelResult = new AnaliseRepresentacaoDocumental.Resultado(NOW,
                AnaliseRepresentacaoDocumental.Conclusao.EVIDENCIA_COMPATIVEL, List.of(), List.of(),
                "gemini", "gemini-3.1-flash-lite", 100, 20);
        var mismatch = new VerificacaoIdentidadeDocumental.Assessment(
                VerificacaoIdentidadeDocumental.IdentityComparison.MISMATCH,
                VerificacaoIdentidadeDocumental.DelegationEvidenceChain.NOT_APPLICABLE,
                VerificacaoIdentidadeDocumental.ApparentValidity.APPARENTLY_CURRENT,
                java.time.LocalDate.of(2027, 1, 1));

        assertEquals(AnaliseRepresentacaoDocumental.Conclusao.INCONCLUSIVO,
                service.effectiveConclusion(modelResult, mismatch, "LEGAL_REPRESENTATIVE"));
    }

    @Test
    void genericRepresentativeFindingIsNormalizedToTheMissingRoleOrSuppressedWhenContradicted() {
        VerificacaoAnaliseOrchestrator service = orchestrator(mock(VerificacaoAiJobStore.class),
                mock(VerificacaoRepresentanteJdbcRepository.class), mock(PrivateVerificationEvidenceStorage.class),
                true, Duration.ofSeconds(1));
        var ref = List.of(new AnaliseRepresentacaoDocumental.ReferenciaPagina(2, 1));
        var generic = AnaliseRepresentacaoDocumental.CodigoDivergencia.REPRESENTANTE_NAO_IDENTIFICADO;
        var delegationResult = new AnaliseRepresentacaoDocumental.Resultado(NOW,
                AnaliseRepresentacaoDocumental.Conclusao.DIVERGENCIA,
                List.of(new AnaliseRepresentacaoDocumental.CampoExtraido(
                                AnaliseRepresentacaoDocumental.Campo.DELEGADO_AUTORIZADO,
                                AnaliseRepresentacaoDocumental.EstadoCampo.ENCONTRADO, "Pessoa Fictícia", ref),
                        new AnaliseRepresentacaoDocumental.CampoExtraido(
                                AnaliseRepresentacaoDocumental.Campo.REPRESENTANTE_AUTORIZANTE,
                                AnaliseRepresentacaoDocumental.EstadoCampo.AUSENTE, null, ref)),
                List.of(), "gemini", "gemini-3.1-flash-lite", 10, 5);

        assertEquals("REPRESENTANTE_AUTORIZANTE_NAO_IDENTIFICADO",
                service.safeDiscrepancyCode(generic, delegationResult, "AUTHORIZED_DELEGATE"));

        var contradiction = new AnaliseRepresentacaoDocumental.Resultado(NOW,
                AnaliseRepresentacaoDocumental.Conclusao.DIVERGENCIA,
                List.of(new AnaliseRepresentacaoDocumental.CampoExtraido(
                        AnaliseRepresentacaoDocumental.Campo.REPRESENTANTE_LEGAL,
                        AnaliseRepresentacaoDocumental.EstadoCampo.ENCONTRADO, "Pessoa Fictícia", ref)),
                List.of(), "gemini", "gemini-3.1-flash-lite", 10, 5);
        assertNull(service.safeDiscrepancyCode(generic, contradiction, "LEGAL_REPRESENTATIVE"),
                "A generic missing-representative discrepancy must not contradict a named, extracted legal representative.");
    }

    @Test
    void disabledProcessingCanReadPreviouslyStoredAdminFindingsWhenReadFlagIsEnabled() {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        var stored = new VerificacaoAiJobStore.StoredAnalysis("COMPLETED", "gemini", "gemini-3.1-flash-lite",
                869, 534, null, "{\"conclusion\":\"DIVERGENCIA\"}", NOW);
        when(jobs.latestForApplicationVersion(14, 1)).thenReturn(Optional.of(stored));
        VerificacaoAnaliseOrchestrator service = new VerificacaoAnaliseOrchestrator(jobs,
                mock(VerificacaoRepresentanteJdbcRepository.class), mock(PrivateVerificationEvidenceStorage.class),
                false, true, "none", "gemini-3.1-flash-lite", Duration.ofSeconds(1),
                Duration.ofSeconds(90), 1024, 1, 100, Clock.fixed(NOW, ZoneOffset.UTC));

        assertSame(stored, service.latestForAdmin(14, 1).orElseThrow());
        assertNull(service.enqueueAfterSubmission(14, new SubmissionRow(20, 14, 1, NOW)));
        assertEquals(VerificacaoAnaliseOrchestrator.ProcessOutcome.DISABLED,
                service.processNext(mock(AnaliseRepresentacaoDocumental.class), WORKER, mock(ExecutorService.class)));
        verify(jobs).latestForApplicationVersion(14, 1);
        verify(jobs, never()).enqueue(anyLong(), anyLong(), anyString(), any());
        verify(jobs, never()).claimNext(any(), any(), any());
    }

    @Test
    void storedFindingsAreNotQueriedWhenResultReadFlagIsDisabled() {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        VerificacaoAnaliseOrchestrator service = new VerificacaoAnaliseOrchestrator(jobs,
                mock(VerificacaoRepresentanteJdbcRepository.class), mock(PrivateVerificationEvidenceStorage.class),
                true, false, "none", "gemini-3.1-flash-lite", Duration.ofSeconds(1),
                Duration.ofSeconds(90), 1024, 1, 100, Clock.fixed(NOW, ZoneOffset.UTC));

        assertTrue(service.latestForAdmin(14, 1).isEmpty());
        verifyNoInteractions(jobs);
    }

    @Test
    void automaticApprovalFlagFailsClosedUntilAnIndependentVerifierExists() {
        var guard = new com.portoajuda.aplicacao_osc.config.VerificacaoAnaliseConfigurationGuard(
                false, true, true, "none", "synthetic-placeholder", "",
                mock(ObjectProvider.class), mock(JdbcTemplate.class));
        assertThrows(IllegalStateException.class, () -> guard.run(mock(ApplicationArguments.class)));
    }

    @Test
    void disabledAnalysisDoesNotInspectTheUnappliedJobSchema() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var guard = new com.portoajuda.aplicacao_osc.config.VerificacaoAnaliseConfigurationGuard(
                false, false, false, "none", "", "", mock(ObjectProvider.class), jdbc);
        guard.run(mock(ApplicationArguments.class));
        verifyNoInteractions(jdbc);
    }

    @Test
    void queueUsesSubmissionProfileAsIdempotencyKey() {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        when(jobs.enqueue(80, 90, "REPRESENTATION_V1", NOW))
                .thenReturn(new VerificacaoAiJobStore.EnqueuedJob(123, false));
        VerificacaoAnaliseOrchestrator service = orchestrator(jobs, mock(VerificacaoRepresentanteJdbcRepository.class),
                mock(PrivateVerificationEvidenceStorage.class), true, Duration.ofSeconds(1));

        var queued = service.enqueueAfterSubmission(80, new SubmissionRow(90, 80, 1, NOW));

        assertEquals(123, queued.id());
        assertFalse(queued.created());
        verify(jobs).enqueue(80, 90, "REPRESENTATION_V1", NOW);
    }

    @Test
    void onlyNewlyInsertedJobsPublishThePostCommitEvaluationScopeEvent() {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(jobs.enqueue(80, 90, "REPRESENTATION_V1", NOW))
                .thenReturn(new VerificacaoAiJobStore.EnqueuedJob(123, true));
        VerificacaoAnaliseOrchestrator service = new VerificacaoAnaliseOrchestrator(jobs,
                mock(VerificacaoRepresentanteJdbcRepository.class), mock(PrivateVerificationEvidenceStorage.class),
                true, true, "gemini", "gemini-3.1-flash-lite", Duration.ofSeconds(1),
                Duration.ofSeconds(90), 1024, 1, 100, Clock.fixed(NOW, ZoneOffset.UTC),
                ZoneOffset.UTC, events);

        service.enqueueAfterSubmission(80, new SubmissionRow(90, 80, 1, NOW));

        verify(events).publishEvent(new VerificacaoAiJobEnqueuedEvent(123));
    }

    @Test
    void completedAnalysisPersistsOnlyStructuredCodesAndPageReferences() throws Exception {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        VerificacaoRepresentanteJdbcRepository verification = mock(VerificacaoRepresentanteJdbcRepository.class);
        PrivateVerificationEvidenceStorage storage = mock(PrivateVerificationEvidenceStorage.class);
        Job job = new Job(5, 80, 90, 1, "REPRESENTATION_V1", 1, 3);
        when(jobs.claimNext(eq(WORKER), eq(NOW), any())).thenReturn(Optional.of(job));
        when(verification.findById(80, false)).thenReturn(Optional.of(
                new ApplicationRow(80, 40, 11, "LEGAL_REPRESENTATIVE", "PENDING", 1, 1, null, NOW)));
        when(verification.analysisApplicationContext(80)).thenReturn(Optional.of(
                new VerificacaoRepresentanteJdbcRepository.AnalysisApplicationContext(
                        "ASSOCIACAO FICTICIA DE TESTE", "00000000000000", "LEGAL_REPRESENTATIVE",
                        "Ana Maria de Souza", "Ana Souza")));
        when(verification.findSubmission(80, 1)).thenReturn(Optional.of(new SubmissionRow(90, 80, 1, NOW)));
        when(verification.listActiveAnalysisEvidence(80, 90, 1)).thenReturn(List.of(
                new AnalysisEvidenceRow(99, "REPRESENTATION_PROOF", "application/pdf", SYNTHETIC_PDF.length,
                        "applications/40/80/v1/fictitious.pdf")));
        when(verification.evidenceStorageKey(80, 99, 1)).thenReturn(Optional.of("applications/40/80/v1/fictitious.pdf"));
        when(storage.readPrivate("applications/40/80/v1/fictitious.pdf", SYNTHETIC_PDF.length)).thenReturn(SYNTHETIC_PDF);
        AnaliseRepresentacaoDocumental provider = mock(AnaliseRepresentacaoDocumental.class);
        var result = new AnaliseRepresentacaoDocumental.Resultado(NOW,
                AnaliseRepresentacaoDocumental.Conclusao.DIVERGENCIA,
                List.of(new AnaliseRepresentacaoDocumental.CampoExtraido(
                        AnaliseRepresentacaoDocumental.Campo.REPRESENTANTE_LEGAL,
                        AnaliseRepresentacaoDocumental.EstadoCampo.ENCONTRADO,
                        "Ana Souza",
                        List.of(new AnaliseRepresentacaoDocumental.ReferenciaPagina(1, 1)))),
                List.of(new AnaliseRepresentacaoDocumental.Divergencia(
                        AnaliseRepresentacaoDocumental.CodigoDivergencia.DOCUMENTOS_CONFLITANTES,
                        AnaliseRepresentacaoDocumental.Gravidade.MEDIA,
                        List.of(new AnaliseRepresentacaoDocumental.ReferenciaPagina(1, 2)))),
                "openai", "gpt-6-luna", 1000, 200);
        when(provider.analisar(any())).thenReturn(result);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            VerificacaoAnaliseOrchestrator service = orchestrator(jobs, verification, storage, true,
                    Duration.ofSeconds(1));
            assertEquals(VerificacaoAnaliseOrchestrator.ProcessOutcome.COMPLETED,
                    service.processNext(provider, WORKER, executor));
        } finally {
            executor.shutdownNow();
        }

        ArgumentCaptor<String> persistedSummary = ArgumentCaptor.forClass(String.class);
        verify(jobs).complete(eq(job), eq(WORKER), eq("openai"), eq("gpt-6-luna"), eq(1000), eq(200),
                eq("REPRESENTATION_FINDINGS_V2"), persistedSummary.capture(), anyString(), eq(NOW));
        ArgumentCaptor<AnaliseRepresentacaoDocumental.Solicitacao> requestSent =
                ArgumentCaptor.forClass(AnaliseRepresentacaoDocumental.Solicitacao.class);
        verify(provider).analisar(requestSent.capture());
        assertEquals(1, requestSent.getValue().evidencias().getFirst().evidenceId());
        assertFalse(requestSent.getValue().toString().contains("Ana Maria de Souza"));
        assertFalse(requestSent.getValue().toString().contains("Ana Souza"),
                "Account civil and social names stay in the backend comparator, outside provider input.");
        assertTrue(persistedSummary.getValue().contains("REPRESENTANTE"));
        assertTrue(persistedSummary.getValue().contains("\"evidenceId\":99"),
                "Provider-local references must be mapped back to private evidence IDs only after processing.");
        assertTrue(persistedSummary.getValue().contains("\"page\":2"));
        assertTrue(persistedSummary.getValue().contains("\"identityComparison\":\"MATCHES_REGISTERED_DATA\""));
        assertFalse(persistedSummary.getValue().contains("Ana Souza"),
                "Extracted personal values are ephemeral and omitted from the durable summary.");
        verify(verification, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void invalidEvidenceReferenceIsRejectedAndCannotBecomeAnApproval() throws Exception {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        VerificacaoRepresentanteJdbcRepository verification = mock(VerificacaoRepresentanteJdbcRepository.class);
        PrivateVerificationEvidenceStorage storage = mock(PrivateVerificationEvidenceStorage.class);
        Job job = new Job(5, 80, 90, 1, "REPRESENTATION_V1", 1, 3);
        when(jobs.claimNext(eq(WORKER), eq(NOW), any())).thenReturn(Optional.of(job));
        when(verification.findById(80, false)).thenReturn(Optional.of(
                new ApplicationRow(80, 40, 11, "LEGAL_REPRESENTATIVE", "PENDING", 1, 1, null, NOW)));
        when(verification.analysisApplicationContext(80)).thenReturn(Optional.of(
                new VerificacaoRepresentanteJdbcRepository.AnalysisApplicationContext(
                        "ASSOCIACAO FICTICIA DE TESTE", "00000000000000", "LEGAL_REPRESENTATIVE")));
        when(verification.findSubmission(80, 1)).thenReturn(Optional.of(new SubmissionRow(90, 80, 1, NOW)));
        when(verification.listActiveAnalysisEvidence(80, 90, 1)).thenReturn(List.of(
                new AnalysisEvidenceRow(99, "REPRESENTATION_PROOF", "application/pdf", SYNTHETIC_PDF.length,
                        "applications/40/80/v1/fictitious.pdf")));
        when(verification.evidenceStorageKey(80, 99, 1)).thenReturn(Optional.of("applications/40/80/v1/fictitious.pdf"));
        when(storage.readPrivate(anyString(), anyLong())).thenReturn(SYNTHETIC_PDF);
        AnaliseRepresentacaoDocumental provider = mock(AnaliseRepresentacaoDocumental.class);
        when(provider.analisar(any())).thenReturn(new AnaliseRepresentacaoDocumental.Resultado(NOW,
                AnaliseRepresentacaoDocumental.Conclusao.EVIDENCIA_COMPATIVEL, List.of(),
                List.of(new AnaliseRepresentacaoDocumental.Divergencia(
                        AnaliseRepresentacaoDocumental.CodigoDivergencia.OUTRA,
                        AnaliseRepresentacaoDocumental.Gravidade.BAIXA,
                        List.of(new AnaliseRepresentacaoDocumental.ReferenciaPagina(999, 1)))),
                "openai", "gpt-6-luna", 100, 20));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertEquals(VerificacaoAnaliseOrchestrator.ProcessOutcome.HUMAN_REVIEW_REQUIRED,
                    orchestrator(jobs, verification, storage, true, Duration.ofSeconds(1))
                            .processNext(provider, WORKER, executor));
        } finally {
            executor.shutdownNow();
        }
        verify(jobs).failPermanently(eq(job), eq(WORKER), eq("INVALID_RESULT"), eq(NOW));
        verify(jobs, never()).complete(any(), any(), anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString(), anyString(), any());
        verify(verification, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void providerTimeoutIsBoundedAndSchedulesOnlyTheConfiguredRetry() throws Exception {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        VerificacaoRepresentanteJdbcRepository verification = mock(VerificacaoRepresentanteJdbcRepository.class);
        PrivateVerificationEvidenceStorage storage = mock(PrivateVerificationEvidenceStorage.class);
        Job job = new Job(5, 80, 90, 1, "REPRESENTATION_V1", 1, 3);
        when(jobs.claimNext(eq(WORKER), eq(NOW), any())).thenReturn(Optional.of(job));
        when(verification.findById(80, false)).thenReturn(Optional.of(
                new ApplicationRow(80, 40, 11, "LEGAL_REPRESENTATIVE", "PENDING", 1, 1, null, NOW)));
        when(verification.analysisApplicationContext(80)).thenReturn(Optional.of(
                new VerificacaoRepresentanteJdbcRepository.AnalysisApplicationContext(
                        "ASSOCIACAO FICTICIA DE TESTE", "00000000000000", "LEGAL_REPRESENTATIVE")));
        when(verification.findSubmission(80, 1)).thenReturn(Optional.of(new SubmissionRow(90, 80, 1, NOW)));
        when(verification.listActiveAnalysisEvidence(80, 90, 1)).thenReturn(List.of(
                new AnalysisEvidenceRow(99, "REPRESENTATION_PROOF", "application/pdf", SYNTHETIC_PDF.length,
                        "applications/40/80/v1/fictitious.pdf")));
        when(verification.evidenceStorageKey(80, 99, 1)).thenReturn(Optional.of("applications/40/80/v1/fictitious.pdf"));
        when(storage.readPrivate(anyString(), anyLong())).thenReturn(SYNTHETIC_PDF);
        CountDownLatch entered = new CountDownLatch(1);
        AnaliseRepresentacaoDocumental provider = request -> {
            entered.countDown();
            new CountDownLatch(1).await();
            return null;
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertEquals(VerificacaoAnaliseOrchestrator.ProcessOutcome.RETRY_SCHEDULED,
                    orchestrator(jobs, verification, storage, true, Duration.ofMillis(25))
                            .processNext(provider, WORKER, executor));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        verify(jobs).fail(eq(job), eq(WORKER), eq("PROVIDER_TIMEOUT"), eq(NOW.plusSeconds(5)), eq(NOW));
        verify(verification, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void quotaExhaustionFailsTheJobForHumanReviewWithoutRetryingTheProvider() throws Exception {
        VerificacaoAiJobStore jobs = mock(VerificacaoAiJobStore.class);
        VerificacaoRepresentanteJdbcRepository verification = mock(VerificacaoRepresentanteJdbcRepository.class);
        PrivateVerificationEvidenceStorage storage = mock(PrivateVerificationEvidenceStorage.class);
        Job job = new Job(5, 80, 90, 1, "REPRESENTATION_V1", 1, 3);
        when(jobs.claimNext(eq(WORKER), eq(NOW), any())).thenReturn(Optional.of(job));
        when(verification.findById(80, false)).thenReturn(Optional.of(
                new ApplicationRow(80, 40, 11, "LEGAL_REPRESENTATIVE", "PENDING", 1, 1, null, NOW)));
        when(verification.findSubmission(80, 1)).thenReturn(Optional.of(new SubmissionRow(90, 80, 1, NOW)));
        when(verification.analysisApplicationContext(80)).thenReturn(Optional.of(
                new VerificacaoRepresentanteJdbcRepository.AnalysisApplicationContext(
                        "ASSOCIACAO FICTICIA DE TESTE", "00000000000000", "LEGAL_REPRESENTATIVE")));
        when(verification.listActiveAnalysisEvidence(80, 90, 1)).thenReturn(List.of(
                new AnalysisEvidenceRow(99, "REPRESENTATION_PROOF", "application/pdf", SYNTHETIC_PDF.length,
                        "applications/40/80/v1/fictitious.pdf")));
        when(verification.evidenceStorageKey(80, 99, 1)).thenReturn(Optional.of("applications/40/80/v1/fictitious.pdf"));
        when(storage.readPrivate(anyString(), anyLong())).thenReturn(SYNTHETIC_PDF);
        AnaliseRepresentacaoDocumental provider = request -> {
            throw new AnaliseRepresentacaoDocumental.FalhaProvedor("PROVIDER_QUOTA_EXHAUSTED", false);
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertEquals(VerificacaoAnaliseOrchestrator.ProcessOutcome.HUMAN_REVIEW_REQUIRED,
                    orchestrator(jobs, verification, storage, true, Duration.ofSeconds(1))
                            .processNext(provider, WORKER, executor));
        } finally {
            executor.shutdownNow();
        }
        verify(jobs).failPermanently(eq(job), eq(WORKER), eq("PROVIDER_QUOTA_EXHAUSTED"), eq(NOW));
        verify(jobs, never()).fail(eq(job), any(), anyString(), any(), any());
        verify(verification, never()).updateStatus(anyLong(), anyString());
    }

    private VerificacaoAnaliseOrchestrator orchestrator(VerificacaoAiJobStore jobs,
            VerificacaoRepresentanteJdbcRepository verification, PrivateVerificationEvidenceStorage storage,
            boolean enabled, Duration timeout) {
        return new VerificacaoAnaliseOrchestrator(jobs, verification, storage, enabled, "openai", "gpt-6-luna",
                timeout, Duration.ofSeconds(90), 30L * 1024 * 1024, 40, 2000,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
