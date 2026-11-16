package com.portoajuda.aplicacao_osc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.admin.AdminOperationAuditService;
import com.portoajuda.aplicacao_osc.dto.request.RequestRevisaoVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestIniciarVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestReaplicarVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestReabrirVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.response.VerificacaoRepresentanteDTO;
import com.portoajuda.aplicacao_osc.dto.response.VerificacaoRepresentanteDetalheAdminDTO;
import com.portoajuda.aplicacao_osc.dto.response.VerificacaoRepresentanteFilaDTO;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.ApplicationRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.EvidenceRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.EvidenceHistoryRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.QueueRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.ReviewRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.SubmissionRow;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VerificacaoRepresentanteService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_EVIDENCE_PER_TYPE_AND_VERSION = 5;
    private static final int MAX_APPLICATION_CYCLES_PER_ORG = 3;
    private static final int MAX_SUBMISSION_VERSIONS = 4;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE_NUMBER = 1000;
    private final VerificacaoRepresentanteJdbcRepository repository;
    private final OscMembrosRepository memberships;
    private final OscRepository organizations;
    private final PrivateVerificationEvidenceStorage storage;
    private final AdminAuthorizationService adminAuthorization;
    private final AdminOperationAuditService operationAudit;
    private final VerificacaoAnaliseOrchestrator aiAnalysis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;

    public VerificacaoRepresentanteService(
            VerificacaoRepresentanteJdbcRepository repository,
            OscMembrosRepository memberships,
            OscRepository organizations,
            PrivateVerificationEvidenceStorage storage,
            AdminAuthorizationService adminAuthorization,
            AdminOperationAuditService operationAudit,
            VerificacaoAnaliseOrchestrator aiAnalysis,
            @Value("${app.ngo-verification.enabled:false}") boolean enabled) {
        this.repository = repository;
        this.memberships = memberships;
        this.organizations = organizations;
        this.storage = storage;
        this.adminAuthorization = adminAuthorization;
        this.operationAudit = operationAudit;
        this.aiAnalysis = aiAnalysis;
        this.enabled = enabled;
    }

    public boolean isEnabled() { return enabled; }

    public String validateRegistrationRepresentation(String value) {
        if (!enabled) return null;
        try {
            return TipoRepresentacaoOsc.valueOf(value == null ? "" : value.trim()).name();
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe se você é representante legal ou pessoa delegada autorizada.");
        }
    }

    @Transactional
    public void createInitialApplication(int oscId, int applicantUserId, String representationType) {
        if (!enabled) return;
        String normalized = validateRegistrationRepresentation(representationType);
        try {
            repository.createApplication(oscId, applicantUserId, normalized);
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe uma solicitação de verificação para esta ONG.");
        }
    }

    public boolean isPubliclyEligible(int oscId) {
        return !enabled || repository.isPubliclyEligible(oscId);
    }

    public void requireOperationallyVerified(int oscId) {
        if (!enabled) return;
        if (!repository.isPubliclyEligible(oscId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "A ONG precisa concluir a verificação de representação antes de publicar conteúdo.");
        }
    }

    @Transactional(readOnly = true)
    public VerificacaoRepresentanteDTO applicantApplication(int oscId, Usuario actor) {
        requireEnabled();
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }
        Optional<ApplicationRow> found = repository.findByOsc(oscId, false);
        if (found.isEmpty()) {
            requireApplicationManager(oscId, actor.getId());
            boolean archived = organizations.findById(oscId).map(org -> org.isArquivada()).orElse(true);
            boolean legacyExempt = repository.isLegacyExempt(oscId);
            return new VerificacaoRepresentanteDTO(null, oscId, null, "NOT_STARTED", null, 0,
                    null, !archived && legacyExempt, archived
                            ? "A ONG arquivada não pode iniciar uma nova solicitação."
                            : legacyExempt
                                    ? "A adesão é voluntária e não altera a visibilidade ou o status operacional existentes."
                                    : "A verificação é necessária antes da visibilidade pública e da criação comum de conteúdo.",
                    List.of(), legacyExempt, 0, MAX_APPLICATION_CYCLES_PER_ORG, false);
        }
        ApplicationRow application = applicantRow(oscId, actor, false);
        return applicantView(application);
    }

    @Transactional
    public VerificacaoRepresentanteDTO startApplication(
            int oscId, RequestIniciarVerificacaoDTO request, Usuario actor) {
        requireEnabled();
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }
        if (request == null || !request.confirmPendingAndVisibilityEffect()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirme o efeito de iniciar a verificação antes de continuar.");
        }
        String representation = validateRegistrationRepresentation(request.representationType());
        requireApplicationManager(oscId, actor.getId());
        if (!repository.lockOrganization(oscId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ONG não encontrada.");
        boolean archived = organizations.findById(oscId).map(org -> org.isArquivada()).orElse(true);
        if (archived) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Uma ONG arquivada não pode iniciar nova verificação.");
        if (!repository.isLegacyExempt(oscId)) throw conflict("ONGs novas recebem uma solicitação durante o cadastro; não é possível iniciar outra.");
        if (repository.findByOsc(oscId, true).isPresent()) {
            throw conflict("Já existe uma solicitação. Se ela foi recusada, use a ação para corrigir e reenviar.");
        }
        try {
            repository.createApplication(oscId, actor.getId(), representation);
        } catch (DuplicateKeyException duplicate) {
            throw conflict("A solicitação já foi iniciada por uma pessoa autorizada.");
        }
        return applicantView(repository.findByOsc(oscId, false).orElseThrow());
    }

    @Transactional
    public VerificacaoRepresentanteDTO reapplyAfterRejection(
            int oscId, RequestReaplicarVerificacaoDTO request, Usuario actor) {
        requireEnabled();
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }
        if (request == null || !request.confirmNewApplication()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirme a criação de uma nova solicitação sem substituir o histórico anterior.");
        }
        String representation = validateRegistrationRepresentation(request.representationType());
        if (!repository.lockOrganization(oscId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ONG não encontrada.");
        requireApplicationManager(oscId, actor.getId());
        boolean archived = organizations.findById(oscId).map(org -> org.isArquivada()).orElse(true);
        if (archived) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Uma ONG arquivada não pode iniciar nova verificação.");
        ApplicationRow previous = repository.findByOsc(oscId, true)
                .orElseThrow(() -> conflict("Não há solicitação recusada para corrigir."));
        if (!previous.applicantUserIdEquals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada.");
        }
        if (!"REJECTED".equals(previous.status())) {
            throw conflict("Somente uma solicitação recusada pode ser reapresentada.");
        }
        if (previous.applicationNumber() >= MAX_APPLICATION_CYCLES_PER_ORG) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "O limite de três solicitações por ONG foi atingido. Entre em contato com o suporte.");
        }
        try {
            repository.createReapplication(oscId, actor.getId(), representation, previous);
        } catch (DuplicateKeyException duplicate) {
            throw conflict("Já existe uma solicitação ativa para esta ONG.");
        }
        return applicantView(repository.findByOsc(oscId, false).orElseThrow());
    }

    @Transactional
    public VerificacaoRepresentanteDTO uploadEvidence(
            int oscId, String documentType, MultipartFile file, Usuario actor) {
        requireEnabled();
        ApplicationRow application = applicantRow(oscId, actor, true);
        String type = normalizeDocumentType(documentType);
        if (!"PENDING".equals(application.status()) && !"INFORMATION_REQUESTED".equals(application.status())) {
            throw conflict("A solicitação não está aceitando documentos neste momento.");
        }
        if ("INFORMATION_REQUESTED".equals(application.status())
                && application.currentVersion() >= MAX_SUBMISSION_VERSIONS) {
            Optional<SubmissionRow> current = repository.findSubmission(application.id(), application.currentVersion());
            boolean currentDraft = current.isPresent() && current.get().submittedAt() == null;
            long authorizedExtraVersions = repository.countAdminReopenEvents(application.id());
            if (!currentDraft && authorizedExtraVersions <= application.currentVersion() - MAX_SUBMISSION_VERSIONS) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "O limite normal de quatro versões foi atingido. Um revisor autorizado pode reabrir a solicitação com justificativa.");
            }
        }
        ValidatedFile validated = validateFile(file);
        int version = prepareDraft(application, actor.getId());
        SubmissionRow submission = repository.findSubmission(application.id(), version)
                .orElseThrow(() -> new IllegalStateException("Versão documental não encontrada."));
        if (repository.evidenceCount(submission.id(), type) >= MAX_EVIDENCE_PER_TYPE_AND_VERSION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cada versão aceita até cinco arquivos de cada tipo de documento.");
        }
        String objectKey = storage.store(application.oscId(), application.id(), version,
                validated.extension(), validated.mediaType(), validated.bytes());
        registerRollbackCleanup(objectKey);
        repository.insertEvidence(submission.id(), type, objectKey, validated.mediaType(),
                validated.bytes().length, actor.getId());
        return applicantView(repository.findById(application.id(), false).orElseThrow());
    }

    @Transactional
    public VerificacaoRepresentanteDTO submitForReview(int oscId, Usuario actor) {
        requireEnabled();
        ApplicationRow application = applicantRow(oscId, actor, true);
        if (!"PENDING".equals(application.status()) && !"INFORMATION_REQUESTED".equals(application.status())) {
            throw conflict("A solicitação não pode ser enviada neste estado.");
        }
        int version = application.currentVersion();
        if (version < 1) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Envie os documentos obrigatórios antes de solicitar a análise.");
        SubmissionRow submission = repository.findSubmission(application.id(), version)
                .orElseThrow(() -> new IllegalStateException("Versão documental não encontrada."));
        if (submission.submittedAt() != null) throw conflict("Esta versão já foi enviada para análise.");
        requireDocuments(application, submission.id());
        Instant submittedAt = repository.markSubmitted(application.id(), version);
        if ("INFORMATION_REQUESTED".equals(application.status())) {
            repository.updateStatus(application.id(), "PENDING");
            repository.insertEvent(application.id(), actor.getId(), "RESUBMITTED",
                    "INFORMATION_REQUESTED", "PENDING", null);
        } else {
            repository.insertEvent(application.id(), actor.getId(), "SUBMITTED", "PENDING", "PENDING", null);
        }
        aiAnalysis.enqueueAfterSubmission(application.id(), new SubmissionRow(
                submission.id(), application.id(), version, submittedAt));
        return applicantView(repository.findById(application.id(), false).orElseThrow());
    }

    @Transactional
    public VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO applicantEvidenceSignedAccess(
            int oscId, long evidenceId, Usuario actor) {
        requireEnabled();
        ApplicationRow application = applicantRow(oscId, actor, true);
        return signedEvidence(application.id(), application.currentVersion(), evidenceId);
    }

    @Transactional(readOnly = true)
    public QueuePage queue(String status, int page, int size) {
        requireEnabled();
        adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_READ);
        String normalizedStatus = normalizeQueueStatus(status);
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        if (boundedPage > MAX_PAGE_NUMBER) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida.");
        long offset = (long) boundedPage * boundedSize;
        if (offset > Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida.");
        List<VerificacaoRepresentanteFilaDTO> items = repository.queue(normalizedStatus, boundedSize, (int) offset)
                .stream().map(this::queueDto).toList();
        return new QueuePage(items, boundedPage, boundedSize, repository.countQueue(normalizedStatus));
    }

    @Transactional
    public VerificacaoRepresentanteDetalheAdminDTO adminDetail(long applicationId, Usuario reviewer) {
        requireEnabled();
        adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_READ);
        ReviewRow row = reviewerRow(applicationId);
        return adminView(row);
    }

    /** Internal manual retention path; no public endpoint or scheduled job invokes it in phase 1. */
    @Transactional
    public VerificacaoRepresentanteJdbcRepository.DisposalTicket requestEvidenceDisposal(
            long evidenceId, String justification, Usuario reviewer) {
        requireEnabled();
        AdminActor admin = adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_REVIEW);
        if (justification == null || justification.isBlank() || justification.trim().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe uma justificativa de descarte de até 2.000 caracteres.");
        }
        ApplicationRow application = repository.findApplicationByEvidenceId(evidenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));
        requireIndependentReviewer(application, admin.userId());
        if (reviewer == null || reviewer.getId() == null || reviewer.getId() != admin.userId()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto administrativo inválido.");
        }
        try {
            VerificacaoRepresentanteJdbcRepository.DisposalTicket ticket = repository.requestEvidenceDisposal(
                    evidenceId, admin.userId(), justification.trim());
            operationAudit.record(admin, AdminPermission.NGO_VERIFICATION_REVIEW,
                    "NGO_VERIFICATION_EVIDENCE_DISPOSAL_REQUESTED", "NGO_VERIFICATION_EVIDENCE",
                    Long.toString(evidenceId), "Pedido de descarte sujeito à política aprovada.",
                    "status=ACTIVE", "status=DISPOSAL_REQUESTED");
            return ticket;
        } catch (org.springframework.dao.DataAccessException failure) {
            throw conflict("O descarte não foi iniciado; confirme a política de retenção aprovada e o status do documento.");
        }
    }

    @Transactional
    public void completeEvidenceDisposal(long evidenceId, long requestEventId, Usuario reviewer) {
        requireEnabled();
        AdminActor admin = adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_REVIEW);
        ApplicationRow application = repository.findApplicationByEvidenceId(evidenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));
        requireIndependentReviewer(application, admin.userId());
        if (reviewer == null || reviewer.getId() == null || reviewer.getId() != admin.userId()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto administrativo inválido.");
        }
        repository.completeEvidenceDisposal(evidenceId, requestEventId, admin.userId());
        operationAudit.record(admin, AdminPermission.NGO_VERIFICATION_REVIEW,
                "NGO_VERIFICATION_EVIDENCE_DISPOSED", "NGO_VERIFICATION_EVIDENCE",
                Long.toString(evidenceId), "Arquivo removido após confirmação do Storage.",
                "status=DISPOSAL_REQUESTED", "status=DISPOSED");
    }

    @Transactional
    public void recordEvidenceDisposalFailure(long evidenceId, long requestEventId, Usuario reviewer) {
        requireEnabled();
        AdminActor admin = adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_REVIEW);
        ApplicationRow application = repository.findApplicationByEvidenceId(evidenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));
        requireIndependentReviewer(application, admin.userId());
        if (reviewer == null || reviewer.getId() == null || reviewer.getId() != admin.userId()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto administrativo inválido.");
        }
        repository.recordEvidenceDisposalFailure(evidenceId, requestEventId, admin.userId());
        operationAudit.record(admin, AdminPermission.NGO_VERIFICATION_REVIEW,
                "NGO_VERIFICATION_EVIDENCE_DISPOSAL_RETRY_REQUIRED", "NGO_VERIFICATION_EVIDENCE",
                Long.toString(evidenceId), "A remoção do arquivo falhou; o acesso permanece bloqueado para nova tentativa.",
                "status=DISPOSAL_REQUESTED", "status=DISPOSAL_REQUESTED");
    }

    /** Internal policy activation hook; no endpoint or scheduled job calls it in phase 1. */
    @Transactional
    public long approveEvidenceRetentionPolicy(String approvalReference, String justification) {
        requireEnabled();
        AdminActor admin = adminAuthorization.requirePermission(AdminPermission.SETTINGS_MANAGE);
        if (approvalReference == null || approvalReference.isBlank() || approvalReference.trim().length() > 80
                || justification == null || justification.isBlank() || justification.trim().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe a referência da aprovação e a justificativa para ativar a política de retenção.");
        }
        long approvalEventId = repository.approveRetentionPolicy(admin.userId(),
                approvalReference.trim(), justification.trim());
        operationAudit.record(admin, AdminPermission.SETTINGS_MANAGE,
                "NGO_VERIFICATION_RETENTION_POLICY_APPROVED", "NGO_VERIFICATION_RETENTION_POLICY", "1",
                justification.trim(), "status=PROPOSED", "status=APPROVED");
        return approvalEventId;
    }

    @Transactional
    public VerificacaoRepresentanteDetalheAdminDTO startReview(long applicationId, Usuario reviewer) {
        requireEnabled();
        AdminActor admin = adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_REVIEW);
        ApplicationRow application = repository.findById(applicationId, true)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada."));
        requireIndependentReviewer(application, admin.userId());
        if (!"PENDING".equals(application.status())) throw conflict("A solicitação não está pendente de análise.");
        SubmissionRow submission = repository.findSubmission(application.id(), application.currentVersion())
                .orElseThrow(() -> conflict("A documentação ainda não foi enviada."));
        if (submission.submittedAt() == null) throw conflict("A documentação ainda não foi enviada.");
        repository.updateStatus(application.id(), "UNDER_REVIEW");
        repository.insertEvent(application.id(), admin.userId(), "REVIEW_STARTED", "PENDING", "UNDER_REVIEW", null);
        operationAudit.record(admin, AdminPermission.NGO_VERIFICATION_REVIEW,
                "NGO_VERIFICATION_REVIEW_STARTED", "NGO_VERIFICATION", Long.toString(application.id()),
                "Início da análise documental.", "status=PENDING", "status=UNDER_REVIEW");
        return adminView(repository.findReviewRow(application.id()).orElseThrow());
    }

    @Transactional
    public VerificacaoRepresentanteDetalheAdminDTO decide(
            long applicationId, @Valid RequestRevisaoVerificacaoDTO request, Usuario reviewer) {
        requireEnabled();
        AdminActor admin = adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_REVIEW);
        ApplicationRow application = repository.findById(applicationId, true)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada."));
        requireIndependentReviewer(application, admin.userId());
        if (!"UNDER_REVIEW".equals(application.status())) throw conflict("A solicitação não está em análise.");
        String justification = request == null ? "" : request.justification().trim();
        if (justification.isBlank() || justification.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe uma justificativa de até 2.000 caracteres.");
        }
        if (request == null || request.decision() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma decisão de revisão.");
        }
        String nextStatus = switch (request.decision()) {
            case "REQUEST_INFORMATION" -> "INFORMATION_REQUESTED";
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decisão inválida.");
        };
        if ("APPROVED".equals(nextStatus)) {
            SubmissionRow submission = repository.findSubmission(application.id(), application.currentVersion())
                    .orElseThrow(() -> conflict("A versão enviada não está disponível."));
            if (submission.submittedAt() == null) throw conflict("A versão enviada não está disponível.");
            requireDocuments(application, submission.id());
        }
        String event = switch (nextStatus) {
            case "INFORMATION_REQUESTED" -> "INFORMATION_REQUESTED";
            case "APPROVED" -> "APPROVED";
            default -> "REJECTED";
        };
        repository.insertEvent(application.id(), admin.userId(), event, application.status(), nextStatus, justification);
        repository.updateStatus(application.id(), nextStatus);
        operationAudit.record(admin, AdminPermission.NGO_VERIFICATION_REVIEW,
                "NGO_VERIFICATION_" + event, "NGO_VERIFICATION", Long.toString(application.id()),
                justification, "status=" + application.status(), "status=" + nextStatus);
        return adminView(repository.findReviewRow(application.id()).orElseThrow());
    }

    @Transactional
    public VerificacaoRepresentanteDetalheAdminDTO reopenRejected(
            long applicationId, RequestReabrirVerificacaoDTO request, Usuario reviewer) {
        requireEnabled();
        AdminActor admin = adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_REVIEW);
        ApplicationRow application = repository.findById(applicationId, true)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada."));
        requireIndependentReviewer(application, admin.userId());
        if (reviewer == null || reviewer.getId() == null || reviewer.getId() != admin.userId()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto administrativo inválido.");
        }
        if (!"REJECTED".equals(application.status())) {
            throw conflict("Somente uma solicitação recusada pode ser reaberta.");
        }
        if (application.applicantUserId() == null) {
            throw conflict("A conta solicitante foi removida; esta solicitação não pode ser reaberta para envio de documentos.");
        }
        String justification = request == null ? "" : request.justification().trim();
        if (justification.isBlank() || justification.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe uma justificativa de até 2.000 caracteres.");
        }
        repository.insertEvent(application.id(), admin.userId(), "ADMIN_REOPENED",
                "REJECTED", "INFORMATION_REQUESTED", justification);
        repository.updateStatus(application.id(), "INFORMATION_REQUESTED");
        operationAudit.record(admin, AdminPermission.NGO_VERIFICATION_REVIEW,
                "NGO_VERIFICATION_REOPENED", "NGO_VERIFICATION", Long.toString(application.id()),
                justification, "status=REJECTED", "status=INFORMATION_REQUESTED");
        return adminView(repository.findReviewRow(application.id()).orElseThrow());
    }

    private ApplicationRow applicantRow(int oscId, Usuario actor, boolean lock) {
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }
        ApplicationRow application = repository.findByOsc(oscId, lock)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação de verificação não encontrada."));
        if (!application.applicantUserIdEquals(actor.getId()) || !memberships.existsByOsc_IdAndUsuario_Id(oscId, actor.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação de verificação não encontrada.");
        }
        return application;
    }

    private void requireIndependentReviewer(ApplicationRow application, int reviewerId) {
        if (application.applicantUserIdEquals(reviewerId)
                || memberships.existsByOsc_IdAndUsuario_Id(application.oscId(), reviewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Uma pessoa vinculada à ONG solicitante não pode revisar a própria solicitação.");
        }
    }

    private int prepareDraft(ApplicationRow application, int actorId) {
        int version = application.currentVersion();
        if ("INFORMATION_REQUESTED".equals(application.status())) {
            Optional<SubmissionRow> current = version == 0 ? Optional.empty()
                    : repository.findSubmission(application.id(), version);
            if (current.isEmpty() || current.get().submittedAt() != null) {
                version++;
                repository.createSubmission(application.id(), version, actorId);
                repository.setCurrentVersion(application.id(), version);
            }
            return version;
        }
        if (version == 0) {
            version = 1;
            repository.createSubmission(application.id(), version, actorId);
            repository.setCurrentVersion(application.id(), version);
            return version;
        }
        SubmissionRow current = repository.findSubmission(application.id(), version)
                .orElseThrow(() -> new IllegalStateException("Versão documental não encontrada."));
        if (current.submittedAt() != null) throw conflict("A versão atual já foi enviada para análise.");
        return version;
    }

    private void requireDocuments(ApplicationRow application, long submissionId) {
        if (repository.evidenceCount(submissionId, "REPRESENTATION_PROOF") < 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Envie um documento que comprove os poderes de representação da ONG.");
        }
        if ("AUTHORIZED_DELEGATE".equals(application.representationType())
                && repository.evidenceCount(submissionId, "DELEGATION_AUTHORIZATION") < 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Pessoa delegada deve enviar também a autorização emitida por representante legal.");
        }
    }

    private VerificacaoRepresentanteDTO applicantView(ApplicationRow application) {
        SubmissionRow submission = application.currentVersion() == 0 ? null
                : repository.findSubmission(application.id(), application.currentVersion()).orElse(null);
        List<VerificacaoRepresentanteDTO.EvidenciaDTO> evidence = submission == null ? List.of()
                : repository.listEvidence(application.id(), application.currentVersion()).stream()
                        .map(row -> new VerificacaoRepresentanteDTO.EvidenciaDTO(
                                row.id(), row.type(), row.mediaType(), row.sizeBytes(), row.uploadedAt(),
                                row.disposalStatus(), row.disposalRequestedAt(), row.disposedAt()))
                        .toList();
        return new VerificacaoRepresentanteDTO(application.id(), application.oscId(), null,
                application.status(), application.representationType(), application.currentVersion(),
                submission == null ? null : submission.submittedAt(), false,
                repository.applicantFeedback(application.id()).orElse(null), evidence,
                repository.isLegacyExempt(application.oscId()), application.applicationNumber(),
                MAX_APPLICATION_CYCLES_PER_ORG,
                "REJECTED".equals(application.status())
                        && application.applicationNumber() < MAX_APPLICATION_CYCLES_PER_ORG);
    }

    private VerificacaoRepresentanteDetalheAdminDTO adminView(ReviewRow row) {
        List<VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO> evidence =
                repository.listEvidenceHistory(row.id()).stream()
                        .filter(item -> item.id() != null)
                        .map(item -> "ACTIVE".equals(item.disposalStatus())
                                ? signedEvidence(row.id(), item)
                                : evidenceWithoutAccess(item)).toList();
        return new VerificacaoRepresentanteDetalheAdminDTO(row.id(), row.oscId(), row.organizationName(),
                row.status(), row.representationType(), row.currentVersion(), row.createdAt(), row.submittedAt(), evidence,
                aiAnalysis.latestForAdmin(row.id(), row.currentVersion()).map(this::analysisAdminDto).orElse(null),
                aiAnalysis.isEnabled(), aiAnalysis.areStoredResultsReadable());
    }

    private VerificacaoRepresentanteDetalheAdminDTO.AnaliseAssistidaDTO analysisAdminDto(
            com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore.StoredAnalysis analysis) {
        List<VerificacaoRepresentanteDetalheAdminDTO.CampoAnaliseDTO> fields = List.of();
        List<VerificacaoRepresentanteDetalheAdminDTO.DivergenciaAnaliseDTO> discrepancies = List.of();
        String conclusion = null;
        String identityComparison = null;
        String delegationEvidenceChain = null;
        String apparentValidity = null;
        java.time.LocalDate expiryDate = null;
        String errorCode = safeAnalysisError(analysis.errorCode());
        if ("COMPLETED".equals(analysis.status()) && analysis.resultSummary() != null) {
            try {
                JsonNode summary = objectMapper.readTree(analysis.resultSummary());
                conclusion = oneOf(summary.path("conclusion").asText(null),
                        List.of("EVIDENCIA_COMPATIVEL", "DIVERGENCIA", "INCONCLUSIVO"));
                fields = parseAnalysisFields(summary.path("fields"));
                discrepancies = parseAnalysisDiscrepancies(summary.path("discrepancies"));
                identityComparison = oneOf(summary.path("identityComparison").asText(null), List.of(
                        "MATCHES_REGISTERED_DATA", "MISMATCH", "INSUFFICIENT_EVIDENCE", "INDEPENDENTLY_VERIFIED"));
                delegationEvidenceChain = oneOf(summary.path("delegationEvidenceChain").asText(null), List.of(
                        "NOT_APPLICABLE", "CONSISTENT_DOCUMENTARY_CHAIN", "INCOMPLETE", "CONFLICTING"));
                apparentValidity = oneOf(summary.path("apparentValidity").asText(null), List.of(
                        "NOT_APPLICABLE", "APPARENTLY_CURRENT", "EXPIRED", "UNVERIFIABLE"));
                if (summary.hasNonNull("expiryDate")) {
                    try { expiryDate = java.time.LocalDate.parse(summary.path("expiryDate").asText()); }
                    catch (java.time.format.DateTimeParseException ignored) { expiryDate = null; }
                }
            } catch (Exception invalidSummary) {
                errorCode = "INVALID_RESULT";
            }
        }
        return new VerificacaoRepresentanteDetalheAdminDTO.AnaliseAssistidaDTO(
                safeAnalysisStatus(analysis.status()), conclusion, safeLabel(analysis.provider(), 40),
                safeLabel(analysis.model(), 80), analysis.inputTokens(), analysis.outputTokens(), errorCode,
                analysis.completedAt(), fields, discrepancies, identityComparison,
                delegationEvidenceChain, apparentValidity, expiryDate);
    }

    private List<VerificacaoRepresentanteDetalheAdminDTO.CampoAnaliseDTO> parseAnalysisFields(JsonNode nodes) {
        if (!nodes.isArray()) return List.of();
        List<VerificacaoRepresentanteDetalheAdminDTO.CampoAnaliseDTO> result = new java.util.ArrayList<>();
        for (JsonNode node : nodes) {
            String field = oneOf(node.path("field").asText(null), List.of("NOME_ENTIDADE", "CNPJ", "REPRESENTANTE",
                    "REPRESENTANTE_LEGAL", "DELEGADO_AUTORIZADO", "REPRESENTANTE_AUTORIZANTE",
                    "PODERES_REPRESENTACAO", "ESCOPO_DELEGACAO", "VALIDADE", "ASSINATURA"));
            String status = oneOf(node.path("status").asText(null), List.of("ENCONTRADO", "AUSENTE", "INCERTO", "CONFLITANTE"));
            if (field != null && status != null) {
                result.add(new VerificacaoRepresentanteDetalheAdminDTO.CampoAnaliseDTO(
                        field, status, parseAnalysisReferences(node.path("references"))));
            }
        }
        return List.copyOf(result);
    }

    private List<VerificacaoRepresentanteDetalheAdminDTO.DivergenciaAnaliseDTO> parseAnalysisDiscrepancies(JsonNode nodes) {
        if (!nodes.isArray()) return List.of();
        List<VerificacaoRepresentanteDetalheAdminDTO.DivergenciaAnaliseDTO> result = new java.util.ArrayList<>();
        for (JsonNode node : nodes) {
            String code = oneOf(node.path("code").asText(null), List.of("NOME_ENTIDADE_DIVERGENTE", "CNPJ_DIVERGENTE",
                    "REPRESENTANTE_NAO_IDENTIFICADO", "REPRESENTANTE_LEGAL_NAO_IDENTIFICADO",
                    "DELEGADO_AUTORIZADO_NAO_IDENTIFICADO", "REPRESENTANTE_AUTORIZANTE_NAO_IDENTIFICADO",
                    "PODERES_NAO_COMPROVADOS", "DELEGACAO_SEM_ESCOPO",
                    "DELEGACAO_VENCIDA", "ASSINATURA_INCONCLUSIVA", "DOCUMENTOS_CONFLITANTES", "OUTRA"));
            String severity = oneOf(node.path("severity").asText(null), List.of("BAIXA", "MEDIA", "ALTA"));
            if (code != null && severity != null) {
                result.add(new VerificacaoRepresentanteDetalheAdminDTO.DivergenciaAnaliseDTO(
                        code, severity, parseAnalysisReferences(node.path("references"))));
            }
        }
        return List.copyOf(result);
    }

    private List<VerificacaoRepresentanteDetalheAdminDTO.ReferenciaPaginaDTO> parseAnalysisReferences(JsonNode nodes) {
        if (!nodes.isArray()) return List.of();
        List<VerificacaoRepresentanteDetalheAdminDTO.ReferenciaPaginaDTO> references = new java.util.ArrayList<>();
        for (JsonNode node : nodes) {
            long evidenceId = node.path("evidenceId").asLong(-1);
            int page = node.path("page").asInt(-1);
            if (evidenceId > 0 && page > 0) {
                references.add(new VerificacaoRepresentanteDetalheAdminDTO.ReferenciaPaginaDTO(evidenceId, page));
            }
        }
        return List.copyOf(references);
    }

    private String safeAnalysisStatus(String status) {
        return oneOf(status, List.of("QUEUED", "PROCESSING", "RETRY", "COMPLETED", "FAILED", "SUPERSEDED"));
    }

    private String safeAnalysisError(String error) {
        return oneOf(error, List.of("PROVIDER_UNAVAILABLE", "PROVIDER_TIMEOUT", "PROVIDER_REJECTED",
                "INVALID_RESULT", "EVIDENCE_UNAVAILABLE", "EVIDENCE_LIMIT_EXCEEDED", "WORKER_LEASE_EXPIRED",
                "INTERNAL_RETRYABLE", "PROVIDER_QUOTA_EXHAUSTED", "PROVIDER_REFUSAL", "INCOMPLETE_RESPONSE",
                "INVALID_DOCUMENT", "PROVIDER_CONFIGURATION_ERROR"));
    }

    private String oneOf(String value, List<String> allowed) {
        return value != null && allowed.contains(value) ? value : null;
    }

    private String safeLabel(String value, int maxLength) {
        return value == null || value.length() > maxLength || !value.matches("[A-Za-z0-9._-]+") ? null : value;
    }

    private VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO signedEvidence(
            long applicationId, int version, EvidenceRow evidence) {
        if (!"ACTIVE".equals(evidence.disposalStatus())) {
            return new VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO(
                    evidence.id(), version, null, evidence.type(), null, null, null,
                    evidence.disposalStatus(), evidence.disposalRequestedAt(), evidence.disposedAt(), null, null);
        }
        String key = repository.evidenceStorageKey(applicationId, evidence.id(), version)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));
        PrivateVerificationEvidenceStorage.SignedAccess access = storage.createShortLivedAccess(key);
        return new VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO(
                evidence.id(), version, null, evidence.type(), evidence.mediaType(), evidence.sizeBytes(), evidence.uploadedAt(),
                evidence.disposalStatus(), evidence.disposalRequestedAt(), evidence.disposedAt(), access.url(), access.expiresAt());
    }

    private VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO signedEvidence(
            long applicationId, EvidenceHistoryRow evidence) {
        if (!"ACTIVE".equals(evidence.disposalStatus())) return evidenceWithoutAccess(evidence);
        String key = repository.evidenceStorageKey(applicationId, evidence.id(), evidence.version())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));
        PrivateVerificationEvidenceStorage.SignedAccess access = storage.createShortLivedAccess(key);
        return new VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO(
                evidence.id(), evidence.version(), evidence.submittedAt(), evidence.type(), evidence.mediaType(),
                evidence.sizeBytes(), evidence.uploadedAt(), evidence.disposalStatus(),
                evidence.disposalRequestedAt(), evidence.disposedAt(),
                access.url(), access.expiresAt());
    }

    private VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO evidenceWithoutAccess(EvidenceHistoryRow evidence) {
        return new VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO(
                evidence.id(), evidence.version(), evidence.submittedAt(), evidence.type(), null, null, null,
                evidence.disposalStatus(), evidence.disposalRequestedAt(), evidence.disposedAt(), null, null);
    }

    private VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO signedEvidence(
            long applicationId, int version, long evidenceId) {
        EvidenceRow evidence = repository.listEvidence(applicationId, version).stream()
                .filter(item -> item.id() == evidenceId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));
        if (!"ACTIVE".equals(evidence.disposalStatus())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Este documento não está mais disponível para acesso.");
        }
        return signedEvidence(applicationId, version, evidence);
    }

    private ReviewRow reviewerRow(long applicationId) {
        AdminActor actor = adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_READ);
        ReviewRow row = repository.findReviewRow(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada."));
        ApplicationRow application = repository.findById(applicationId, true).orElseThrow();
        requireIndependentReviewer(application, actor.userId());
        return row;
    }

    private VerificacaoRepresentanteFilaDTO queueDto(QueueRow row) {
        return new VerificacaoRepresentanteFilaDTO(row.id(), row.oscId(), row.organizationName(), row.status(),
                row.representationType(), row.currentVersion(), row.submittedAt());
    }

    private String normalizeQueueStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PENDING", "UNDER_REVIEW", "INFORMATION_REQUESTED", "APPROVED", "REJECTED").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de status inválido.");
        }
        return value;
    }

    private String normalizeDocumentType(String documentType) {
        String value = documentType == null ? "" : documentType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("REPRESENTATION_PROOF", "DELEGATION_AUTHORIZATION").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de documento inválido.");
        }
        return value;
    }

    private ValidatedFile validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() < 1 || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O documento deve ter até 10 MB.");
        }
        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o documento enviado.");
        }
        if (bytes.length < 4 || bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O documento enviado é inválido ou excede 10 MB.");
        }
        if (bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes.length >= 5 && bytes[4] == '-') {
            return new ValidatedFile(bytes, "pdf", "application/pdf");
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return new ValidatedFile(bytes, "jpg", "image/jpeg");
        }
        if ((bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return new ValidatedFile(bytes, "png", "image/png");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato inválido. Envie PDF, JPEG ou PNG válido.");
    }

    private void registerRollbackCleanup(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) cleanupImmediately(objectKey);
            }
        });
    }

    private void cleanupImmediately(String objectKey) {
        try { storage.delete(objectKey); } catch (RuntimeException ignored) { /* no documento nem no log */ }
    }

    private void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "A verificação de representantes não está habilitada nesta instância.");
    }

    private void requireApplicationManager(int oscId, int userId) {
        OscMembros membership = memberships.findByOsc_IdAndUsuario_Id(oscId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Solicitação de verificação não encontrada."));
        if (membership.getCargoOsc() != CargoOsc.CRIADOR && membership.getCargoOsc() != CargoOsc.ADMINISTRADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente a pessoa criadora ou administradora da ONG pode iniciar a verificação.");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record ValidatedFile(byte[] bytes, String extension, String mediaType) { }
    public record QueuePage(List<VerificacaoRepresentanteFilaDTO> items, int page, int size, long totalElements) { }
}
