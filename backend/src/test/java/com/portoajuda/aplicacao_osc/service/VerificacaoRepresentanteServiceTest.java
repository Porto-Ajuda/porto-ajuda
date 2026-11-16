package com.portoajuda.aplicacao_osc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminOperationAuditService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.admin.AdminProfile;
import com.portoajuda.aplicacao_osc.dto.request.RequestIniciarVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestReaplicarVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestReabrirVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestRevisaoVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.response.VerificacaoRepresentanteDetalheAdminDTO;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.ApplicationRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.EvidenceRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.ReviewRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.SubmissionRow;
import com.portoajuda.aplicacao_osc.repository.VerificacaoAiJobStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificacaoRepresentanteServiceTest {
    @Mock VerificacaoRepresentanteJdbcRepository repository;
    @Mock OscMembrosRepository memberships;
    @Mock OscRepository organizations;
    @Mock PrivateVerificationEvidenceStorage storage;
    @Mock AdminAuthorizationService adminAuthorization;
    @Mock AdminOperationAuditService audit;
    @Mock VerificacaoAnaliseOrchestrator aiAnalysis;
    VerificacaoRepresentanteService service;

    @BeforeEach void setup() {
        service = new VerificacaoRepresentanteService(repository, memberships, organizations, storage,
                adminAuthorization, audit, aiAnalysis, true);
    }

    @Test void newRegistrationRequiresAnExplicitRepresentationTypeOnlyWhenFeatureIsEnabled() {
        assertThrows(ResponseStatusException.class, () -> service.validateRegistrationRepresentation(null));
        assertEquals("AUTHORIZED_DELEGATE", service.validateRegistrationRepresentation("AUTHORIZED_DELEGATE"));
        VerificacaoRepresentanteService disabled = new VerificacaoRepresentanteService(repository, memberships,
                organizations, storage, adminAuthorization, audit, aiAnalysis, false);
        assertNull(disabled.validateRegistrationRepresentation(null));
        assertTrue(disabled.isPubliclyEligible(999));
        disabled.createInitialApplication(999, 21, null);
        disabled.requireOperationallyVerified(999);
        verifyNoInteractions(repository);
    }

    @Test void anotherMemberCannotReadApplicantVerificationOrReceiveSignedDocumentAccess() {
        Usuario otherMember = user(22);
        when(repository.findByOsc(50, false)).thenReturn(Optional.of(app(31, 50, 21,
                "LEGAL_REPRESENTATIVE", "PENDING", 1)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.applicantApplication(50, otherMember));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(storage);
        verify(memberships, never()).existsByOsc_IdAndUsuario_Id(50, 22);
    }

    @Test void authorizedAdminDetailExposesOnlyStructuredAdvisoryFindingsAndPageReferences() throws Exception {
        int reviewerId = 900;
        var actor = new AdminActor(reviewerId, UUID.randomUUID(), AdminProfile.SUPER_ADMIN);
        when(adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_READ)).thenReturn(actor);
        ReviewRow review = new ReviewRow(71, 50, "ONG Ficticia", 21, "LEGAL_REPRESENTATIVE", "PENDING", 1,
                Instant.parse("2026-11-14T00:00:00Z"), Instant.parse("2026-11-14T00:01:00Z"));
        when(repository.findReviewRow(71)).thenReturn(Optional.of(review));
        when(repository.findById(71, true)).thenReturn(Optional.of(app(71, 50, 21,
                "LEGAL_REPRESENTATIVE", "PENDING", 1)));
        when(repository.listEvidenceHistory(71)).thenReturn(List.of());
        when(memberships.existsByOsc_IdAndUsuario_Id(50, reviewerId)).thenReturn(false);
        when(aiAnalysis.isEnabled()).thenReturn(false);
        when(aiAnalysis.areStoredResultsReadable()).thenReturn(true);
        when(aiAnalysis.latestForAdmin(71, 1)).thenReturn(Optional.of(new VerificacaoAiJobStore.StoredAnalysis(
                "COMPLETED", "gemini", "gemini-3.1-flash-lite", 868, 467, null,
                "{\"conclusion\":\"INCONCLUSIVO\",\"identityComparison\":\"MATCHES_REGISTERED_DATA\",\"delegationEvidenceChain\":\"CONSISTENT_DOCUMENTARY_CHAIN\",\"apparentValidity\":\"APPARENTLY_CURRENT\",\"expiryDate\":\"2027-01-01\",\"fields\":[{\"field\":\"CNPJ\",\"status\":\"ENCONTRADO\",\"references\":[{\"evidenceId\":77,\"page\":1}]},{\"field\":\"DELEGADO_AUTORIZADO\",\"status\":\"ENCONTRADO\",\"references\":[{\"evidenceId\":77,\"page\":1}]}],\"discrepancies\":[{\"code\":\"PODERES_NAO_COMPROVADOS\",\"severity\":\"ALTA\",\"references\":[{\"evidenceId\":77,\"page\":1}]}]}",
                Instant.parse("2026-11-14T00:02:00Z"))));

        VerificacaoRepresentanteDetalheAdminDTO detail = service.adminDetail(71, user(reviewerId));

        assertNotNull(detail.aiAnalysis());
        assertFalse(detail.aiAnalysisProcessingEnabled(), "Stored findings must remain visible with new processing disabled.");
        assertTrue(detail.aiAnalysisResultsReadable());
        assertEquals("INCONCLUSIVO", detail.aiAnalysis().conclusion());
        assertEquals("MATCHES_REGISTERED_DATA", detail.aiAnalysis().identityComparison());
        assertEquals("CONSISTENT_DOCUMENTARY_CHAIN", detail.aiAnalysis().delegationEvidenceChain());
        assertEquals("APPARENTLY_CURRENT", detail.aiAnalysis().apparentValidity());
        assertEquals(java.time.LocalDate.of(2027, 1, 1), detail.aiAnalysis().expiryDate());
        assertEquals("DELEGADO_AUTORIZADO", detail.aiAnalysis().fields().get(1).field());
        assertEquals("PODERES_NAO_COMPROVADOS", detail.aiAnalysis().discrepancies().getFirst().code());
        assertEquals(new VerificacaoRepresentanteDetalheAdminDTO.ReferenciaPaginaDTO(77, 1),
                detail.aiAnalysis().discrepancies().getFirst().references().getFirst());
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(detail);
        assertFalse(json.contains("extractedValue"));
        assertFalse(json.contains("Ana Social Test"));
    }

    @Test void requestedOrDisposedEvidenceCannotGenerateANewSignedLink() {
        Usuario applicant = user(21);
        when(repository.findByOsc(50, true)).thenReturn(Optional.of(app(31, 50, 21,
                "LEGAL_REPRESENTATIVE", "APPROVED", 1)));
        when(memberships.existsByOsc_IdAndUsuario_Id(50, 21)).thenReturn(true);
        when(repository.listEvidence(31, 1)).thenReturn(List.of(new EvidenceRow(90,
                "REPRESENTATION_PROOF", null, null, null, "DISPOSAL_REQUESTED", Instant.now(), null)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.applicantEvidenceSignedAccess(50, 90, applicant));

        assertEquals(HttpStatus.GONE, error.getStatusCode());
        verify(repository, never()).evidenceStorageKey(anyLong(), anyLong(), anyInt());
        verifyNoInteractions(storage);
    }

    @Test void legacyVisibilityIsExplicitAndUnverifiedApplicationsCannotPublish() {
        when(repository.isPubliclyEligible(80)).thenReturn(true);
        when(repository.isPubliclyEligible(81)).thenReturn(false);
        assertTrue(service.isPubliclyEligible(80));
        service.requireOperationallyVerified(80);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireOperationallyVerified(81));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(repository, times(2)).isPubliclyEligible(80);
        verify(repository).isPubliclyEligible(81);
    }

    @Test void applicantMustBeCurrentCreatorOrAdministratorToOptIntoLegacyVerification() {
        Usuario user = user(21);
        OscMembros member = new OscMembros();
        member.setCargoOsc(CargoOsc.EDITOR);
        when(memberships.findByOsc_IdAndUsuario_Id(50, 21)).thenReturn(Optional.of(member));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.startApplication(50,
                        new RequestIniciarVerificacaoDTO("LEGAL_REPRESENTATIVE", true), user));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test void legacyManagerCanSeeOptInWithoutChangingExistingOperationalState() {
        Usuario applicant = user(21);
        OscMembros member = new OscMembros();
        member.setCargoOsc(CargoOsc.CRIADOR);
        var inactiveOrganization = new com.portoajuda.aplicacao_osc.entity.Osc();
        inactiveOrganization.setStatus(com.portoajuda.aplicacao_osc.enums.StatusOsc.INATIVA);
        when(repository.findByOsc(50, false)).thenReturn(Optional.empty());
        when(memberships.findByOsc_IdAndUsuario_Id(50, 21)).thenReturn(Optional.of(member));
        when(organizations.findById(50)).thenReturn(Optional.of(inactiveOrganization));
        when(repository.isLegacyExempt(50)).thenReturn(true);

        var result = service.applicantApplication(50, applicant);

        assertEquals("NOT_STARTED", result.status());
        assertTrue(result.canStartApplication());
        assertEquals(com.portoajuda.aplicacao_osc.enums.StatusOsc.INATIVA, inactiveOrganization.getStatus());
        verify(repository, never()).createApplication(anyInt(), anyInt(), anyString());
    }

    @Test void documentsMustMatchTheirSignatureAndRemainAssociatedToApplicantVersion() {
        Usuario user = user(21);
        ApplicationRow row = app(31, 50, 21, "LEGAL_REPRESENTATIVE", "PENDING", 0);
        when(repository.findByOsc(50, true)).thenReturn(Optional.of(row));
        when(memberships.existsByOsc_IdAndUsuario_Id(50, 21)).thenReturn(true);
        MockMultipartFile wrong = new MockMultipartFile("arquivo", "proof.pdf", "application/pdf", "not a pdf".getBytes());
        ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
                () -> service.uploadEvidence(50, "REPRESENTATION_PROOF", wrong, user));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        verify(repository, never()).createSubmission(anyLong(), anyInt(), anyInt());

        when(repository.findByOsc(50, true)).thenReturn(Optional.of(row));
        when(repository.findSubmission(31, 1)).thenReturn(Optional.of(new SubmissionRow(77, 31, 1, null)));
        when(repository.findById(31, false)).thenReturn(Optional.of(app(31, 50, 21, "LEGAL_REPRESENTATIVE", "PENDING", 1)));
        when(repository.listEvidence(31, 1)).thenReturn(List.of(new EvidenceRow(
                90, "REPRESENTATION_PROOF", "image/jpeg", 4L, Instant.now(), "ACTIVE", null, null)));
        when(storage.store(eq(50), eq(31L), eq(1), eq("jpg"), eq("image/jpeg"), any(byte[].class)))
                .thenReturn("applications/50/31/v1/synthetic.jpg");
        MockMultipartFile valid = new MockMultipartFile("arquivo", "nome-com-dados-pessoais.jpg", "text/plain",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});
        var result = service.uploadEvidence(50, "REPRESENTATION_PROOF", valid, user);
        assertEquals(31L, result.applicationId());
        assertEquals(1, result.submissionVersion());
        assertEquals("image/jpeg", result.evidence().getFirst().mediaType());
        verify(repository).insertEvidence(77, "REPRESENTATION_PROOF", "applications/50/31/v1/synthetic.jpg",
                "image/jpeg", 4, 21);
    }

    @Test void oversizedDocumentIsRejectedBeforeStorageOrDatabaseWrites() {
        Usuario user = user(21);
        when(repository.findByOsc(50, true)).thenReturn(Optional.of(
                app(31, 50, 21, "LEGAL_REPRESENTATIVE", "PENDING", 0)));
        when(memberships.existsByOsc_IdAndUsuario_Id(50, 21)).thenReturn(true);
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("arquivo", "oversized.pdf", "application/pdf", oversized);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.uploadEvidence(50, "REPRESENTATION_PROOF", file, user));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(repository, never()).createSubmission(anyLong(), anyInt(), anyInt());
        verifyNoInteractions(storage);
    }

    @Test void uploadedObjectIsDeletedIfDatabasePersistenceFailsAndTransactionRollsBack() {
        Usuario user = user(21);
        ApplicationRow row = app(31, 50, 21, "LEGAL_REPRESENTATIVE", "PENDING", 1);
        when(repository.findByOsc(50, true)).thenReturn(Optional.of(row));
        when(memberships.existsByOsc_IdAndUsuario_Id(50, 21)).thenReturn(true);
        when(repository.findSubmission(31, 1)).thenReturn(Optional.of(new SubmissionRow(77, 31, 1, null)));
        when(repository.evidenceCount(77, "REPRESENTATION_PROOF")).thenReturn(0);
        when(storage.store(eq(50), eq(31L), eq(1), eq("jpg"), eq("image/jpeg"), any(byte[].class)))
                .thenReturn("applications/50/31/v1/rollback-test.jpg");
        doThrow(new DataAccessResourceFailureException("synthetic persistence failure"))
                .when(repository).insertEvidence(77, "REPRESENTATION_PROOF",
                        "applications/50/31/v1/rollback-test.jpg", "image/jpeg", 4, 21);
        MockMultipartFile file = new MockMultipartFile("arquivo", "synthetic.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(DataAccessResourceFailureException.class,
                    () -> service.uploadEvidence(50, "REPRESENTATION_PROOF", file, user));
            var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(storage).delete("applications/50/31/v1/rollback-test.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void delegateCannotSubmitWithoutProofAndWrittenAuthorization() {
        Usuario user = user(21);
        ApplicationRow row = app(31, 50, 21, "AUTHORIZED_DELEGATE", "PENDING", 1);
        when(repository.findByOsc(50, true)).thenReturn(Optional.of(row));
        when(memberships.existsByOsc_IdAndUsuario_Id(50, 21)).thenReturn(true);
        when(repository.findSubmission(31, 1)).thenReturn(Optional.of(new SubmissionRow(77, 31, 1, null)));
        when(repository.evidenceCount(77, "REPRESENTATION_PROOF")).thenReturn(1);
        when(repository.evidenceCount(77, "DELEGATION_AUTHORIZATION")).thenReturn(0);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.submitForReview(50, user));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatusCode());
        verify(repository, never()).markSubmitted(anyLong(), anyInt());
    }

    @Test void duplicateSubmissionDoesNotCreateAnotherJobForTheSameSubmittedVersion() {
        Usuario applicant = user(21);
        ApplicationRow pending = app(31, 50, 21, "LEGAL_REPRESENTATIVE", "PENDING", 1);
        SubmissionRow draft = new SubmissionRow(77, 31, 1, null);
        SubmissionRow submitted = new SubmissionRow(77, 31, 1, Instant.now());
        when(repository.findByOsc(50, true)).thenReturn(Optional.of(pending));
        when(memberships.existsByOsc_IdAndUsuario_Id(50, 21)).thenReturn(true);
        when(repository.findSubmission(31, 1)).thenReturn(Optional.of(draft), Optional.of(submitted));
        when(repository.evidenceCount(77, "REPRESENTATION_PROOF")).thenReturn(1);
        when(repository.markSubmitted(31, 1)).thenReturn(submitted.submittedAt());
        when(repository.findById(31, false)).thenReturn(Optional.of(pending));
        when(repository.applicantFeedback(31)).thenReturn(Optional.empty());
        when(repository.isLegacyExempt(50)).thenReturn(false);
        when(repository.listEvidence(31, 1)).thenReturn(List.of());

        service.submitForReview(50, applicant);

        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
                () -> service.submitForReview(50, applicant));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());

        verify(aiAnalysis, times(1)).enqueueAfterSubmission(31, submitted);
        verify(repository).insertEvent(31, 21, "SUBMITTED", "PENDING", "PENDING", null);
    }

    @Test void applicantCannotReviewTheirOwnApplicationEvenWithAdministrativeAssignment() {
        Usuario reviewer = user(21);
        when(adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_REVIEW))
                .thenReturn(new AdminActor(21, UUID.randomUUID(), AdminProfile.PLATFORM_ADMIN));
        when(repository.findById(31, true)).thenReturn(Optional.of(app(31, 50, 21,
                "LEGAL_REPRESENTATIVE", "PENDING", 1)));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.startReview(31, reviewer));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(repository, never()).updateStatus(anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    @Test void voluntaryLegacyEnrollmentRequiresExplicitConfirmation() {
        Usuario applicant = user(21);
        ResponseStatusException missingConsent = assertThrows(ResponseStatusException.class,
                () -> service.startApplication(50,
                        new RequestIniciarVerificacaoDTO("LEGAL_REPRESENTATIVE", false), applicant));
        assertEquals(HttpStatus.BAD_REQUEST, missingConsent.getStatusCode());
        verify(repository, never()).createApplication(anyInt(), anyInt(), anyString());
    }

    @Test void authorizedManagerCanStartLegacyVerificationWithoutChangingOperationalStatus() {
        Usuario applicant = user(21);
        OscMembros member = new OscMembros();
        member.setCargoOsc(CargoOsc.ADMINISTRADOR);
        when(memberships.findByOsc_IdAndUsuario_Id(50, 21)).thenReturn(Optional.of(member));
        when(repository.lockOrganization(50)).thenReturn(true);
        var organization = new com.portoajuda.aplicacao_osc.entity.Osc();
        organization.setStatus(com.portoajuda.aplicacao_osc.enums.StatusOsc.INATIVA);
        when(organizations.findById(50)).thenReturn(Optional.of(organization));
        when(repository.isLegacyExempt(50)).thenReturn(true);
        when(repository.findByOsc(50, true)).thenReturn(Optional.empty());
        when(repository.findByOsc(50, false)).thenReturn(Optional.of(app(31, 50, 21,
                "AUTHORIZED_DELEGATE", "PENDING", 0)));
        when(repository.applicantFeedback(31)).thenReturn(Optional.empty());

        var result = service.startApplication(50,
                new RequestIniciarVerificacaoDTO("AUTHORIZED_DELEGATE", true), applicant);

        assertEquals("PENDING", result.status());
        assertTrue(result.legacyExempt());
        assertFalse(result.canReapply());
        assertEquals("AUTHORIZED_DELEGATE", result.representationType());
        verify(repository).createApplication(50, 21, "AUTHORIZED_DELEGATE");
        assertEquals(com.portoajuda.aplicacao_osc.enums.StatusOsc.INATIVA, organization.getStatus());
    }

    @Test void rejectedApplicantCanCreateANewCycleWithoutOverwritingTheRejectedRecord() {
        Usuario applicant = user(21);
        OscMembros member = new OscMembros();
        member.setCargoOsc(CargoOsc.CRIADOR);
        when(repository.lockOrganization(50)).thenReturn(true);
        when(memberships.findByOsc_IdAndUsuario_Id(50, 21)).thenReturn(Optional.of(member));
        when(organizations.findById(50)).thenReturn(Optional.of(new com.portoajuda.aplicacao_osc.entity.Osc()));
        ApplicationRow rejected = new ApplicationRow(31, 50, 21, "LEGAL_REPRESENTATIVE", "REJECTED", 1,
                1, null, Instant.now());
        when(repository.findByOsc(50, true)).thenReturn(Optional.of(rejected));
        ApplicationRow pending = new ApplicationRow(32, 50, 21, "AUTHORIZED_DELEGATE", "PENDING", 0,
                2, 31L, Instant.now());
        when(repository.findByOsc(50, false)).thenReturn(Optional.of(pending));
        when(repository.isLegacyExempt(50)).thenReturn(true);
        when(repository.applicantFeedback(32)).thenReturn(Optional.empty());

        var result = service.reapplyAfterRejection(50,
                new RequestReaplicarVerificacaoDTO("AUTHORIZED_DELEGATE", true), applicant);

        assertEquals(32L, result.applicationId());
        assertEquals("PENDING", result.status());
        assertEquals(2, result.applicationNumber());
        assertEquals(31L, pending.parentApplicationId());
        verify(repository).createReapplication(50, 21, "AUTHORIZED_DELEGATE", rejected);
        verify(repository, never()).updateStatus(31, "PENDING");
    }

    @Test void onlyTheOriginalApplicantCanCreateARejectedApplicationCycle() {
        Usuario differentManager = user(22);
        OscMembros member = new OscMembros();
        member.setCargoOsc(CargoOsc.ADMINISTRADOR);
        when(repository.lockOrganization(50)).thenReturn(true);
        when(memberships.findByOsc_IdAndUsuario_Id(50, 22)).thenReturn(Optional.of(member));
        when(organizations.findById(50)).thenReturn(Optional.of(new com.portoajuda.aplicacao_osc.entity.Osc()));
        when(repository.findByOsc(50, true)).thenReturn(Optional.of(new ApplicationRow(31, 50, 21,
                "LEGAL_REPRESENTATIVE", "REJECTED", 1, 1, null, Instant.now())));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.reapplyAfterRejection(50,
                        new RequestReaplicarVerificacaoDTO("LEGAL_REPRESENTATIVE", true), differentManager));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(repository, never()).createReapplication(anyInt(), anyInt(), anyString(), any());
    }

    @Test void informationRequestResubmissionsAreBoundedPerApplication() {
        Usuario applicant = user(21);
        when(repository.findByOsc(50, true)).thenReturn(Optional.of(app(31, 50, 21,
                "LEGAL_REPRESENTATIVE", "INFORMATION_REQUESTED", 4)));
        when(memberships.existsByOsc_IdAndUsuario_Id(50, 21)).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("arquivo", "valid.pdf", "application/pdf",
                "%PDF-synthetic".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.uploadEvidence(50, "REPRESENTATION_PROOF", file, applicant));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        verify(repository, never()).createSubmission(anyLong(), anyInt(), anyInt());
        verifyNoInteractions(storage);
    }

    @Test void reapplicationRequiresRejectedStateAndIsLimitedToThreeCycles() {
        Usuario applicant = user(21);
        OscMembros member = new OscMembros();
        member.setCargoOsc(CargoOsc.ADMINISTRADOR);
        when(repository.lockOrganization(50)).thenReturn(true);
        when(memberships.findByOsc_IdAndUsuario_Id(50, 21)).thenReturn(Optional.of(member));
        when(organizations.findById(50)).thenReturn(Optional.of(new com.portoajuda.aplicacao_osc.entity.Osc()));
        when(repository.findByOsc(50, true)).thenReturn(Optional.of(new ApplicationRow(33, 50, 21,
                "LEGAL_REPRESENTATIVE", "REJECTED", 4, 3, 32L, Instant.now())));
        ResponseStatusException limited = assertThrows(ResponseStatusException.class,
                () -> service.reapplyAfterRejection(50,
                        new RequestReaplicarVerificacaoDTO("LEGAL_REPRESENTATIVE", true), applicant));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getStatusCode());
        verify(repository, never()).createReapplication(anyInt(), anyInt(), anyString(), any());
    }

    @Test void authorizedIndependentReviewerCanReopenRejectedApplicationWithAuditedReason() {
        Usuario reviewer = user(99);
        AdminActor actor = new AdminActor(99, UUID.randomUUID(), AdminProfile.PLATFORM_ADMIN);
        when(adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_REVIEW)).thenReturn(actor);
        when(repository.findById(31, true)).thenReturn(Optional.of(app(31, 50, 21,
                "LEGAL_REPRESENTATIVE", "REJECTED", 4)));
        when(memberships.existsByOsc_IdAndUsuario_Id(50, 99)).thenReturn(false);
        when(repository.findReviewRow(31)).thenReturn(Optional.of(new ReviewRow(31, 50, "ONG Sintética", 21,
                "LEGAL_REPRESENTATIVE", "INFORMATION_REQUESTED", 4, Instant.now(), null)));
        when(repository.listEvidenceHistory(31)).thenReturn(List.of());

        var result = service.reopenRejected(31,
                new RequestReabrirVerificacaoDTO("Nova documentação identificada para correção."), reviewer);

        assertEquals("INFORMATION_REQUESTED", result.status());
        verify(repository).insertEvent(31, 99, "ADMIN_REOPENED", "REJECTED", "INFORMATION_REQUESTED",
                "Nova documentação identificada para correção.");
        verify(repository).updateStatus(31, "INFORMATION_REQUESTED");
        verify(audit).record(actor, AdminPermission.NGO_VERIFICATION_REVIEW,
                "NGO_VERIFICATION_REOPENED", "NGO_VERIFICATION", "31",
                "Nova documentação identificada para correção.", "status=REJECTED", "status=INFORMATION_REQUESTED");
    }

    @Test void applicantCannotReopenOwnRejectedApplication() {
        Usuario reviewer = user(21);
        when(adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_REVIEW))
                .thenReturn(new AdminActor(21, UUID.randomUUID(), AdminProfile.PLATFORM_ADMIN));
        when(repository.findById(31, true)).thenReturn(Optional.of(app(31, 50, 21,
                "LEGAL_REPRESENTATIVE", "REJECTED", 4)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.reopenRejected(31,
                        new RequestReabrirVerificacaoDTO("Justificativa sintética."), reviewer));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(repository, never()).updateStatus(anyLong(), anyString());
    }

    @Test void legacyPendingApplicationDoesNotRemoveExistingVisibilityOrOperationalAccess() {
        when(repository.isPubliclyEligible(50)).thenReturn(true);
        assertTrue(service.isPubliclyEligible(50));
        assertDoesNotThrow(() -> service.requireOperationallyVerified(50));
        verify(repository, times(2)).isPubliclyEligible(50);
    }

    @Test void reviewerDecisionRecordsHumanReasonAndDoesNotActivateOrganization() {
        Usuario reviewer = user(99);
        AdminActor actor = new AdminActor(99, UUID.randomUUID(), AdminProfile.PLATFORM_ADMIN);
        when(adminAuthorization.requirePermission(AdminPermission.NGO_VERIFICATION_REVIEW)).thenReturn(actor);
        when(repository.findById(31, true)).thenReturn(Optional.of(app(31, 50, 21,
                "LEGAL_REPRESENTATIVE", "UNDER_REVIEW", 1)));
        when(memberships.existsByOsc_IdAndUsuario_Id(50, 99)).thenReturn(false);
        when(repository.findSubmission(31, 1)).thenReturn(Optional.of(new SubmissionRow(77, 31, 1, Instant.now())));
        when(repository.evidenceCount(77, "REPRESENTATION_PROOF")).thenReturn(1);
        when(repository.findReviewRow(31)).thenReturn(Optional.of(new ReviewRow(31, 50, "ONG Sintética", 21,
                "LEGAL_REPRESENTATIVE", "APPROVED", 1, Instant.now(), Instant.now())));
        when(repository.listEvidenceHistory(31)).thenReturn(List.of(new VerificacaoRepresentanteJdbcRepository.EvidenceHistoryRow(
                1, Instant.now(), 90L, "REPRESENTATION_PROOF", "application/pdf", 128L,
                Instant.now(), "ACTIVE", null, null)));
        when(repository.evidenceStorageKey(31, 90L, 1)).thenReturn(Optional.of("applications/50/31/v1/synthetic.pdf"));
        when(storage.createShortLivedAccess("applications/50/31/v1/synthetic.pdf"))
                .thenReturn(new PrivateVerificationEvidenceStorage.SignedAccess("https://storage.example/signed", Instant.now().plusSeconds(300)));

        var result = service.decide(31,
                new RequestRevisaoVerificacaoDTO("APPROVE", "Documento de representação confirmado."), reviewer);

        assertEquals("APPROVED", result.status());
        assertEquals("ONG Sintética", result.organizationName());
        assertEquals(1, result.evidence().getFirst().submissionVersion());
        verify(repository).insertEvent(31, 99, "APPROVED", "UNDER_REVIEW", "APPROVED",
                "Documento de representação confirmado.");
        verify(repository).updateStatus(31, "APPROVED");
        verify(audit).record(actor, AdminPermission.NGO_VERIFICATION_REVIEW,
                "NGO_VERIFICATION_APPROVED", "NGO_VERIFICATION", "31",
                "Documento de representação confirmado.", "status=UNDER_REVIEW", "status=APPROVED");
        verify(organizations, never()).save(any(com.portoajuda.aplicacao_osc.entity.Osc.class));
    }

    private static ApplicationRow app(long id, int oscId, int applicant, String type, String status, int version) {
        return new ApplicationRow(id, oscId, applicant, type, status, version, 1, null, Instant.now());
    }

    private static Usuario user(int id) {
        Usuario user = new Usuario();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
