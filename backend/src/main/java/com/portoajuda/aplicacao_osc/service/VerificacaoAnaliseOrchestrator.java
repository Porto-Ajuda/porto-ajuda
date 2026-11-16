package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore;
import com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore.Job;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.AnalysisEvidenceRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.ApplicationRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.SubmissionRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Provider-neutral job orchestration. Results are advisory, references are checked against the
 * submitted evidence set, and no operation in this class can change an application decision.
 */
@Service
public class VerificacaoAnaliseOrchestrator {
    public static final String ANALYSIS_PROFILE = "REPRESENTATION_V1";
    private static final String RESULT_SCHEMA_VERSION = "REPRESENTATION_FINDINGS_V2";
    private static final Pattern SAFE_PROVIDER_ID = Pattern.compile("[A-Za-z0-9._-]{1,40}");
    private static final Pattern SAFE_MODEL_ID = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    private final VerificacaoAiJobStore jobs;
    private final VerificacaoRepresentanteJdbcRepository verification;
    private final PrivateVerificationEvidenceStorage storage;
    private final boolean enabled;
    private final boolean resultsReadEnabled;
    private final String configuredProvider;
    private final String configuredModel;
    private final Duration timeout;
    private final Duration lease;
    private final long maxTotalBytes;
    private final int maxPages;
    private final int maxOutputTokens;
    private final Clock clock;
    private final ZoneId validityZone;
    private final ApplicationEventPublisher events;

    @Autowired
    public VerificacaoAnaliseOrchestrator(
            VerificacaoAiJobStore jobs,
            VerificacaoRepresentanteJdbcRepository verification,
            PrivateVerificationEvidenceStorage storage,
            @Value("${app.ngo-verification.ai-analysis.enabled:false}") boolean enabled,
            @Value("${app.ngo-verification.ai-analysis.results-read-enabled:false}") boolean resultsReadEnabled,
            @Value("${app.ngo-verification.ai-analysis.provider:none}") String configuredProvider,
            @Value("${app.ngo-verification.ai-analysis.model:gpt-6.1-terra}") String configuredModel,
            @Value("${app.ngo-verification.ai-analysis.timeout:30s}") Duration timeout,
            @Value("${app.ngo-verification.ai-analysis.lease:90s}") Duration lease,
            @Value("${app.ngo-verification.ai-analysis.max-total-bytes:31457280}") long maxTotalBytes,
            @Value("${app.ngo-verification.ai-analysis.max-pages:40}") int maxPages,
            @Value("${app.ngo-verification.ai-analysis.max-output-tokens:2000}") int maxOutputTokens,
            @Value("${app.ngo-verification.ai-analysis.validity-zone:America/Sao_Paulo}") String validityZone,
            ApplicationEventPublisher events) {
        this(jobs, verification, storage, enabled, resultsReadEnabled, configuredProvider, configuredModel, timeout, lease,
                maxTotalBytes, maxPages, maxOutputTokens, Clock.systemUTC(), ZoneId.of(validityZone), events);
    }

    VerificacaoAnaliseOrchestrator(
            VerificacaoAiJobStore jobs,
            VerificacaoRepresentanteJdbcRepository verification,
            PrivateVerificationEvidenceStorage storage,
            boolean enabled,
            String configuredProvider,
            String configuredModel,
            Duration timeout,
            Duration lease,
            long maxTotalBytes,
            int maxPages,
            int maxOutputTokens,
            Clock clock) {
        this(jobs, verification, storage, enabled, enabled, configuredProvider, configuredModel, timeout, lease,
                maxTotalBytes, maxPages, maxOutputTokens, clock);
    }

    VerificacaoAnaliseOrchestrator(
            VerificacaoAiJobStore jobs,
            VerificacaoRepresentanteJdbcRepository verification,
            PrivateVerificationEvidenceStorage storage,
            boolean enabled,
            boolean resultsReadEnabled,
            String configuredProvider,
            String configuredModel,
            Duration timeout,
            Duration lease,
            long maxTotalBytes,
            int maxPages,
            int maxOutputTokens,
            Clock clock) {
        this(jobs, verification, storage, enabled, resultsReadEnabled, configuredProvider, configuredModel, timeout, lease,
                maxTotalBytes, maxPages, maxOutputTokens, clock, ZoneId.of("America/Sao_Paulo"));
    }

    VerificacaoAnaliseOrchestrator(
            VerificacaoAiJobStore jobs,
            VerificacaoRepresentanteJdbcRepository verification,
            PrivateVerificationEvidenceStorage storage,
            boolean enabled,
            boolean resultsReadEnabled,
            String configuredProvider,
            String configuredModel,
            Duration timeout,
            Duration lease,
            long maxTotalBytes,
            int maxPages,
            int maxOutputTokens,
            Clock clock,
            ZoneId validityZone) {
        this(jobs, verification, storage, enabled, resultsReadEnabled, configuredProvider, configuredModel, timeout,
                lease, maxTotalBytes, maxPages, maxOutputTokens, clock, validityZone, event -> { });
    }

    VerificacaoAnaliseOrchestrator(
            VerificacaoAiJobStore jobs,
            VerificacaoRepresentanteJdbcRepository verification,
            PrivateVerificationEvidenceStorage storage,
            boolean enabled,
            boolean resultsReadEnabled,
            String configuredProvider,
            String configuredModel,
            Duration timeout,
            Duration lease,
            long maxTotalBytes,
            int maxPages,
            int maxOutputTokens,
            Clock clock,
            ZoneId validityZone,
            ApplicationEventPublisher events) {
        this.jobs = jobs;
        this.verification = verification;
        this.storage = storage;
        this.enabled = enabled;
        this.resultsReadEnabled = resultsReadEnabled;
        this.configuredProvider = configuredProvider;
        this.configuredModel = configuredModel;
        this.timeout = timeout;
        this.lease = lease;
        this.maxTotalBytes = maxTotalBytes;
        this.maxPages = maxPages;
        this.maxOutputTokens = maxOutputTokens;
        this.clock = clock;
        this.validityZone = validityZone;
        this.events = events;
        if (timeout == null || timeout.isZero() || timeout.isNegative() || lease == null
                || lease.compareTo(timeout) <= 0 || maxTotalBytes < 1 || maxTotalBytes > 100L * 1024 * 1024
                || maxPages < 1 || maxPages > 500 || maxOutputTokens < 1 || maxOutputTokens > 8000) {
            throw new IllegalArgumentException("Configuração de análise documental inválida.");
        }
    }

    /** Called inside the existing submission transaction; disabled mode makes no table query. */
    public VerificacaoAiJobStore.EnqueuedJob enqueueAfterSubmission(long applicationId,
                                                                    SubmissionRow submission) {
        if (!enabled) return null;
        if (submission == null || submission.applicationId() != applicationId || submission.submittedAt() == null) {
            throw new IllegalArgumentException("A análise exige uma versão documental enviada.");
        }
        VerificacaoAiJobStore.EnqueuedJob queued = jobs.enqueue(applicationId, submission.id(), ANALYSIS_PROFILE, clock.instant());
        if (queued.created()) events.publishEvent(new VerificacaoAiJobEnqueuedEvent(queued.id()));
        return queued;
    }

    public ProcessOutcome processNext(AnaliseRepresentacaoDocumental provider, UUID workerId,
                                     ExecutorService boundedExecutor) {
        if (!enabled || provider == null || workerId == null || boundedExecutor == null) {
            return ProcessOutcome.DISABLED;
        }
        Instant now = clock.instant();
        jobs.recoverExpiredLeases(now);
        Job job = jobs.claimNext(workerId, now, lease).orElse(null);
        if (job == null) return ProcessOutcome.IDLE;
        return processClaimedJob(job, provider, workerId, boundedExecutor);
    }

    public boolean isEnabled() { return enabled; }

    public boolean areStoredResultsReadable() { return resultsReadEnabled; }

    /** Called only from the already-authorized administrative detail path. */
    public Optional<VerificacaoAiJobStore.StoredAnalysis> latestForAdmin(long applicationId, int version) {
        if (!resultsReadEnabled || applicationId < 1 || version < 1) return Optional.empty();
        return jobs.latestForApplicationVersion(applicationId, version);
    }

    /** Claims and processes exactly one job id without scanning, recovering, or polling other jobs. */
    ProcessOutcome processSpecificJob(long jobId, AnaliseRepresentacaoDocumental provider, UUID workerId,
                                      ExecutorService boundedExecutor) {
        if (!enabled || provider == null || workerId == null || boundedExecutor == null) {
            return ProcessOutcome.DISABLED;
        }
        Job job = jobs.claimById(jobId, workerId, clock.instant(), lease).orElse(null);
        if (job == null) return ProcessOutcome.IDLE;
        return processClaimedJob(job, provider, workerId, boundedExecutor);
    }

    private ProcessOutcome processClaimedJob(Job job, AnaliseRepresentacaoDocumental provider, UUID workerId,
                                             ExecutorService boundedExecutor) {
        Future<AnaliseRepresentacaoDocumental.Resultado> future = null;
        try {
            ApplicationRow application = verification.findById(job.applicationId(), false).orElse(null);
            SubmissionRow submission = verification.findSubmission(job.applicationId(), job.submissionVersion()).orElse(null);
            if (application == null || submission == null || submission.submittedAt() == null
                    || submission.id() != job.submissionId()
                    || application.currentVersion() != job.submissionVersion()
                    || !("PENDING".equals(application.status()) || "UNDER_REVIEW".equals(application.status()))) {
                jobs.supersede(job, workerId, clock.instant());
                return ProcessOutcome.SUPERSEDED;
            }

            LoadedRequest loaded = loadRequest(job);
            AnaliseRepresentacaoDocumental.Solicitacao request = loaded.request();
            future = boundedExecutor.submit(() -> provider.analisar(request));
            AnaliseRepresentacaoDocumental.Resultado result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            validateResult(result, request);
            VerificacaoIdentidadeDocumental.Assessment identityAssessment = VerificacaoIdentidadeDocumental.assess(
                    loaded.representationType(), loaded.applicantLegalName(), loaded.applicantSocialName(), result,
                    loaded.providerEvidenceTypes(), clock, validityZone);
            ApplicationRow latest = verification.findById(job.applicationId(), false).orElse(null);
            if (latest == null || latest.currentVersion() != job.submissionVersion()
                    || !("PENDING".equals(latest.status()) || "UNDER_REVIEW".equals(latest.status()))) {
                jobs.supersede(job, workerId, clock.instant());
                return ProcessOutcome.SUPERSEDED;
            }
            String summary = safeSummary(result, effectiveConclusion(result, identityAssessment,
                            loaded.representationType()),
                    loaded.providerToDatabaseEvidenceIds(), identityAssessment, loaded.representationType());
            jobs.complete(job, workerId, result.provider(), result.model(), Math.toIntExact(result.inputTokens()),
                    Math.toIntExact(result.outputTokens()), RESULT_SCHEMA_VERSION, summary, sha256(summary),
                    clock.instant());
            return ProcessOutcome.COMPLETED;
        } catch (TimeoutException timeoutFailure) {
            if (future != null) future.cancel(true);
            scheduleFailure(job, workerId, "PROVIDER_TIMEOUT");
            return ProcessOutcome.RETRY_SCHEDULED;
        } catch (InvalidAnalysisResult invalid) {
            if (future != null) future.cancel(true);
            jobs.failPermanently(job, workerId, invalid.errorCode(), clock.instant());
            return ProcessOutcome.HUMAN_REVIEW_REQUIRED;
        } catch (AnaliseRepresentacaoDocumental.FalhaProvedor failure) {
            if (future != null) future.cancel(true);
            persistProviderFailure(job, workerId, failure);
            return failure.retryable() ? ProcessOutcome.RETRY_SCHEDULED : ProcessOutcome.HUMAN_REVIEW_REQUIRED;
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof AnaliseRepresentacaoDocumental.FalhaProvedor providerFailure) {
                persistProviderFailure(job, workerId, providerFailure);
                return providerFailure.retryable() ? ProcessOutcome.RETRY_SCHEDULED : ProcessOutcome.HUMAN_REVIEW_REQUIRED;
            }
            scheduleFailure(job, workerId, "PROVIDER_REJECTED");
            return ProcessOutcome.RETRY_SCHEDULED;
        } catch (Exception failure) {
            if (future != null) future.cancel(true);
            scheduleFailure(job, workerId, "PROVIDER_REJECTED");
            return ProcessOutcome.RETRY_SCHEDULED;
        }
    }

    private LoadedRequest loadRequest(Job job) {
        List<AnalysisEvidenceRow> rows = verification.listActiveAnalysisEvidence(
                job.applicationId(), job.submissionId(), job.submissionVersion());
        if (rows.isEmpty() || rows.size() > 10) throw new InvalidAnalysisResult("EVIDENCE_LIMIT_EXCEEDED");
        long totalBytes = 0;
        List<AnaliseRepresentacaoDocumental.Evidencia> documents = new ArrayList<>();
        Map<Long, Long> providerToDatabaseEvidenceIds = new HashMap<>();
        Map<Long, String> providerEvidenceTypes = new HashMap<>();
        for (AnalysisEvidenceRow row : rows) {
            if (row.sizeBytes() < 1 || row.sizeBytes() > 10L * 1024 * 1024) {
                throw new InvalidAnalysisResult("EVIDENCE_LIMIT_EXCEEDED");
            }
            totalBytes = Math.addExact(totalBytes, row.sizeBytes());
            if (totalBytes > maxTotalBytes) throw new InvalidAnalysisResult("EVIDENCE_LIMIT_EXCEEDED");
            String key = verification.evidenceStorageKey(job.applicationId(), row.id(), job.submissionVersion())
                    .orElseThrow(() -> new InvalidAnalysisResult("EVIDENCE_UNAVAILABLE"));
            byte[] bytes;
            try {
                bytes = storage.readPrivate(key, row.sizeBytes());
            } catch (RuntimeException unavailable) {
                throw new InvalidAnalysisResult("EVIDENCE_UNAVAILABLE");
            }
            if (bytes.length != row.sizeBytes()) throw new InvalidAnalysisResult("EVIDENCE_UNAVAILABLE");
            long providerEvidenceRef = documents.size() + 1L;
            providerToDatabaseEvidenceIds.put(providerEvidenceRef, row.id());
            providerEvidenceTypes.put(providerEvidenceRef, row.documentType());
            documents.add(new AnaliseRepresentacaoDocumental.Evidencia(
                    providerEvidenceRef, row.documentType(), row.mediaType(), bytes));
        }
        var context = verification.analysisApplicationContext(job.applicationId())
                .orElseThrow(() -> new InvalidAnalysisResult("EVIDENCE_UNAVAILABLE"));
        String cnpj = context.organizationCnpj() == null ? ""
                : context.organizationCnpj().replaceAll("[^0-9]", "");
        try {
            var applicationContext = new AnaliseRepresentacaoDocumental.ContextoAplicacao(
                    context.organizationName(), cnpj, context.representationType());
            return new LoadedRequest(new AnaliseRepresentacaoDocumental.Solicitacao(
                    applicationContext, documents, RESULT_SCHEMA_VERSION, maxPages, maxOutputTokens),
                    Map.copyOf(providerToDatabaseEvidenceIds), Map.copyOf(providerEvidenceTypes),
                    context.representationType(), context.applicantLegalName(), context.applicantSocialName());
        } catch (IllegalArgumentException invalidContext) {
            throw new InvalidAnalysisResult("EVIDENCE_UNAVAILABLE");
        }
    }

    private void validateResult(AnaliseRepresentacaoDocumental.Resultado result,
                                AnaliseRepresentacaoDocumental.Solicitacao request) {
        if (result == null || !SAFE_PROVIDER_ID.matcher(result.provider()).matches()
                || !SAFE_MODEL_ID.matcher(result.model()).matches()
                || !configuredProvider.equals(result.provider()) || !configuredModel.equals(result.model())
                || result.outputTokens() > maxOutputTokens) {
            throw new InvalidAnalysisResult("INVALID_RESULT");
        }
        Set<Long> evidenceIds = request.evidencias().stream()
                .map(AnaliseRepresentacaoDocumental.Evidencia::evidenceId).collect(java.util.stream.Collectors.toSet());
        List<AnaliseRepresentacaoDocumental.ReferenciaPagina> references = new ArrayList<>();
        result.camposExtraidos().forEach(field -> references.addAll(field.referencias()));
        result.divergencias().forEach(discrepancy -> references.addAll(discrepancy.referencias()));
        if (references.size() > 200 || references.stream().anyMatch(reference ->
                !evidenceIds.contains(reference.evidenceId()) || reference.pageNumber() > maxPages)) {
            throw new InvalidAnalysisResult("INVALID_RESULT");
        }
    }

    AnaliseRepresentacaoDocumental.Conclusao effectiveConclusion(
            AnaliseRepresentacaoDocumental.Resultado result,
            VerificacaoIdentidadeDocumental.Assessment assessment, String representationType) {
        boolean entityDivergence = result.divergencias().stream().anyMatch(item ->
                item.codigo() == AnaliseRepresentacaoDocumental.CodigoDivergencia.CNPJ_DIVERGENTE
                        || item.codigo() == AnaliseRepresentacaoDocumental.CodigoDivergencia.NOME_ENTIDADE_DIVERGENTE);
        boolean meaningfulDivergence = result.divergencias().stream()
                .anyMatch(item -> safeDiscrepancyCode(item.codigo(), result, representationType) != null);
        if (assessment.identityComparison() == VerificacaoIdentidadeDocumental.IdentityComparison.MISMATCH
                && !entityDivergence) {
            // A name mismatch is a human-review signal, never an automated finding of fraud.
            return AnaliseRepresentacaoDocumental.Conclusao.INCONCLUSIVO;
        }
        if (result.conclusao() == AnaliseRepresentacaoDocumental.Conclusao.DIVERGENCIA && !meaningfulDivergence) {
            return AnaliseRepresentacaoDocumental.Conclusao.INCONCLUSIVO;
        }
        if (result.conclusao() == AnaliseRepresentacaoDocumental.Conclusao.EVIDENCIA_COMPATIVEL
                && (assessment.identityComparison() != VerificacaoIdentidadeDocumental.IdentityComparison.MATCHES_REGISTERED_DATA
                || (assessment.apparentValidity() != VerificacaoIdentidadeDocumental.ApparentValidity.APPARENTLY_CURRENT
                && assessment.apparentValidity() != VerificacaoIdentidadeDocumental.ApparentValidity.NOT_APPLICABLE)
                || assessment.delegationEvidenceChain() == VerificacaoIdentidadeDocumental.DelegationEvidenceChain.INCOMPLETE
                || assessment.delegationEvidenceChain() == VerificacaoIdentidadeDocumental.DelegationEvidenceChain.CONFLICTING)) {
            return AnaliseRepresentacaoDocumental.Conclusao.INCONCLUSIVO;
        }
        return result.conclusao();
    }

    /** Persist only enums, numeric evidence references, safe dates and usage metadata. */
    private String safeSummary(AnaliseRepresentacaoDocumental.Resultado result,
                               AnaliseRepresentacaoDocumental.Conclusao conclusion,
                               Map<Long, Long> providerToDatabaseEvidenceIds,
                               VerificacaoIdentidadeDocumental.Assessment identityAssessment,
                               String representationType) {
        StringBuilder json = new StringBuilder(512);
        json.append("{\"conclusion\":\"").append(conclusion.name())
                .append("\",\"provider\":\"").append(result.provider())
                .append("\",\"model\":\"").append(result.model())
                .append("\",\"identityComparison\":\"").append(identityAssessment.identityComparison().name())
                .append("\",\"delegationEvidenceChain\":\"").append(identityAssessment.delegationEvidenceChain().name())
                .append("\",\"apparentValidity\":\"").append(identityAssessment.apparentValidity().name()).append('"');
        LocalDate expiryDate = identityAssessment.expiryDate();
        if (expiryDate != null) json.append(",\"expiryDate\":\"").append(expiryDate).append('"');
        json.append(",\"fields\":[");
        for (int i = 0; i < result.camposExtraidos().size(); i++) {
            if (i > 0) json.append(',');
            AnaliseRepresentacaoDocumental.CampoExtraido field = result.camposExtraidos().get(i);
            json.append("{\"field\":\"").append(field.campo().name()).append("\",\"status\":\"")
                    .append(field.estado().name()).append("\",\"references\":");
            appendReferences(json, field.referencias(), providerToDatabaseEvidenceIds);
            json.append('}');
        }
        json.append("],\"discrepancies\":[");
        boolean firstDiscrepancy = true;
        for (AnaliseRepresentacaoDocumental.Divergencia discrepancy : result.divergencias()) {
            String discrepancyCode = safeDiscrepancyCode(discrepancy.codigo(), result, representationType);
            if (discrepancyCode == null) continue;
            if (!firstDiscrepancy) json.append(',');
            firstDiscrepancy = false;
            json.append("{\"code\":\"").append(discrepancyCode)
                    .append("\",\"severity\":\"").append(discrepancy.gravidade().name())
                    .append("\",\"references\":");
            appendReferences(json, discrepancy.referencias(), providerToDatabaseEvidenceIds);
            json.append('}');
        }
        return json.append("],\"inputTokens\":").append(result.inputTokens())
                .append(",\"outputTokens\":").append(result.outputTokens()).append('}').toString();
    }

    String safeDiscrepancyCode(AnaliseRepresentacaoDocumental.CodigoDivergencia code,
                               AnaliseRepresentacaoDocumental.Resultado result,
                               String representationType) {
        if (code != AnaliseRepresentacaoDocumental.CodigoDivergencia.REPRESENTANTE_NAO_IDENTIFICADO) {
            return code.name();
        }
        if ("AUTHORIZED_DELEGATE".equals(representationType)) {
            boolean delegateFound = hasExtractedField(result, "DELEGADO_AUTORIZADO");
            if (!delegateFound) return "DELEGADO_AUTORIZADO_NAO_IDENTIFICADO";
            boolean authorizerFound = hasExtractedField(result, "REPRESENTANTE_AUTORIZANTE");
            return authorizerFound ? null : "REPRESENTANTE_AUTORIZANTE_NAO_IDENTIFICADO";
        }
        boolean legalRepresentativeFound = hasExtractedField(result, "REPRESENTANTE_LEGAL")
                || hasExtractedField(result, "REPRESENTANTE");
        return legalRepresentativeFound ? null : "REPRESENTANTE_LEGAL_NAO_IDENTIFICADO";
    }

    private boolean hasExtractedField(AnaliseRepresentacaoDocumental.Resultado result, String fieldName) {
        return result.camposExtraidos().stream().anyMatch(field -> field.campo().name().equals(fieldName)
                && field.estado() == AnaliseRepresentacaoDocumental.EstadoCampo.ENCONTRADO
                && field.extractedValue() != null && !field.extractedValue().isBlank());
    }

    private void appendReferences(StringBuilder json, List<AnaliseRepresentacaoDocumental.ReferenciaPagina> refs,
                                  Map<Long, Long> providerToDatabaseEvidenceIds) {
        json.append('[');
        for (int i = 0; i < refs.size(); i++) {
            if (i > 0) json.append(',');
            var reference = refs.get(i);
            Long databaseEvidenceId = providerToDatabaseEvidenceIds.get(reference.evidenceId());
            if (databaseEvidenceId == null) throw new InvalidAnalysisResult("INVALID_RESULT");
            json.append("{\"evidenceId\":").append(databaseEvidenceId)
                    .append(",\"page\":").append(reference.pageNumber()).append('}');
        }
        json.append(']');
    }

    private void scheduleFailure(Job job, UUID workerId, String errorCode) {
        long delaySeconds = Math.min(300, 5L << Math.max(0, job.attempt() - 1));
        jobs.fail(job, workerId, errorCode, clock.instant().plusSeconds(delaySeconds), clock.instant());
    }

    private void persistProviderFailure(Job job, UUID workerId,
                                        AnaliseRepresentacaoDocumental.FalhaProvedor failure) {
        if (failure.retryable()) {
            scheduleFailure(job, workerId, failure.codigo());
        } else {
            jobs.failPermanently(job, workerId, failure.codigo(), clock.instant());
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("Não foi possível calcular o resumo da análise.");
        }
    }

    public enum ProcessOutcome { DISABLED, IDLE, COMPLETED, RETRY_SCHEDULED, HUMAN_REVIEW_REQUIRED, SUPERSEDED }

    private record LoadedRequest(AnaliseRepresentacaoDocumental.Solicitacao request,
                                 Map<Long, Long> providerToDatabaseEvidenceIds,
                                 Map<Long, String> providerEvidenceTypes,
                                 String representationType,
                                 String applicantLegalName,
                                 String applicantSocialName) {
        @Override public String toString() {
            return "LoadedRequest[request=" + request + ", evidenceReferences=" + providerToDatabaseEvidenceIds.size()
                    + ", applicantNames=[REDACTED]]";
        }
    }

    private static final class InvalidAnalysisResult extends RuntimeException {
        private final String errorCode;
        private InvalidAnalysisResult(String errorCode) {
            super(errorCode);
            if (!List.of("INVALID_RESULT", "EVIDENCE_UNAVAILABLE", "EVIDENCE_LIMIT_EXCEEDED").contains(errorCode)) {
                throw new IllegalArgumentException("Invalid analysis error code.");
            }
            this.errorCode = errorCode;
        }
        private String errorCode() { return errorCode; }
    }
}
