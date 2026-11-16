package com.portoajuda.aplicacao_osc.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** JDBC repository intentionally avoids Hibernate schema validation before the additive migration is applied. */
@Repository
public class VerificacaoRepresentanteJdbcRepository {
    private final JdbcTemplate jdbc;

    public VerificacaoRepresentanteJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long createApplication(int oscId, int applicantUserId, String representationType) {
        return createApplication(oscId, applicantUserId, representationType, 1, null, "APPLICATION_CREATED");
    }

    public long createReapplication(int oscId, int applicantUserId, String representationType,
                                   ApplicationRow previous) {
        if (previous == null || previous.oscId() != oscId || !"REJECTED".equals(previous.status())) {
            throw new IllegalArgumentException("A solicitação anterior não está recusada para esta ONG.");
        }
        if (previous.applicationNumber() >= 3) {
            throw new IllegalStateException("O limite de solicitações de verificação desta ONG foi atingido.");
        }
        return createApplication(oscId, applicantUserId, representationType,
                previous.applicationNumber() + 1, previous.id(), "REAPPLICATION_CREATED");
    }

    private long createApplication(int oscId, int applicantUserId, String representationType,
                                   int applicationNumber, Long parentApplicationId, String action) {
        Long id = jdbc.queryForObject("""
                INSERT INTO public.ngo_representative_verification_applications
                    (osc_id, applicant_user_id, representation_type, status, current_submission_version,
                     application_number, parent_application_id)
                VALUES (?, ?, ?, 'PENDING', 0, ?, ?)
                RETURNING id
                """, Long.class, oscId, applicantUserId, representationType, applicationNumber, parentApplicationId);
        if (id == null) throw new IllegalStateException("Não foi possível iniciar a verificação da ONG.");
        insertEvent(id, applicantUserId, action, null, "PENDING", null);
        return id;
    }

    /** Serializes all application-cycle creation for an organization. Call inside a transaction. */
    public boolean lockOrganization(int oscId) {
        return !jdbc.query("SELECT id FROM public.oscs WHERE id=? FOR UPDATE",
                (rs, row) -> rs.getInt(1), oscId).isEmpty();
    }

    public Optional<ApplicationRow> findByOsc(int oscId, boolean forUpdate) {
        String sql = """
                SELECT a.id, a.osc_id, a.applicant_user_id, a.representation_type, a.status,
                       a.current_submission_version, a.application_number, a.parent_application_id, a.created_at
                  FROM public.ngo_representative_verification_applications a
                 WHERE a.osc_id = ?
                 ORDER BY a.application_number DESC
                 LIMIT 1
                """ + (forUpdate ? " FOR UPDATE" : "");
        return jdbc.query(sql, APPLICATION_ROW, oscId).stream().findFirst();
    }

    public Optional<ApplicationRow> findById(long applicationId, boolean forUpdate) {
        String sql = """
                SELECT a.id, a.osc_id, a.applicant_user_id, a.representation_type, a.status,
                       a.current_submission_version, a.application_number, a.parent_application_id, a.created_at
                  FROM public.ngo_representative_verification_applications a
                 WHERE a.id = ?
                """ + (forUpdate ? " FOR UPDATE" : "");
        return jdbc.query(sql, APPLICATION_ROW, applicationId).stream().findFirst();
    }

    public Optional<ApplicationRow> findApplicationByEvidenceId(long evidenceId) {
        return jdbc.query("""
                SELECT a.id, a.osc_id, a.applicant_user_id, a.representation_type, a.status,
                       a.current_submission_version, a.application_number, a.parent_application_id, a.created_at
                  FROM public.ngo_representative_verification_evidence e
                  JOIN public.ngo_representative_verification_submissions s ON s.id=e.submission_id
                  JOIN public.ngo_representative_verification_applications a ON a.id=s.application_id
                 WHERE e.id=?
                """, APPLICATION_ROW, evidenceId).stream().findFirst();
    }

    public Optional<String> verificationStatus(int oscId) {
        return jdbc.query("SELECT status FROM public.ngo_representative_verification_applications WHERE osc_id=?",
                (rs, row) -> rs.getString(1), oscId).stream().findFirst();
    }

    public boolean isPubliclyEligible(int oscId) {
        Boolean eligible = jdbc.queryForObject(
                "SELECT public.ngo_representative_is_publicly_eligible(?)", Boolean.class, oscId);
        return Boolean.TRUE.equals(eligible);
    }

    public boolean isLegacyExempt(int oscId) {
        Boolean legacy = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public.ngo_representative_verification_legacy_exemptions WHERE osc_id=?)
                """, Boolean.class, oscId);
        return Boolean.TRUE.equals(legacy);
    }

    public int createSubmission(long applicationId, int version, int submittedBy) {
        return jdbc.update("""
                INSERT INTO public.ngo_representative_verification_submissions
                    (application_id, version_number, submitted_by_user_id)
                VALUES (?, ?, ?)
                """, applicationId, version, submittedBy);
    }

    public void setCurrentVersion(long applicationId, int version) {
        jdbc.update("""
                UPDATE public.ngo_representative_verification_applications
                   SET current_submission_version=?, updated_at=clock_timestamp()
                 WHERE id=?
                """, version, applicationId);
    }

    public Optional<SubmissionRow> findSubmission(long applicationId, int version) {
        return jdbc.query("""
                SELECT id, application_id, version_number, submitted_at
                  FROM public.ngo_representative_verification_submissions
                 WHERE application_id=? AND version_number=?
                """, SUBMISSION_ROW, applicationId, version).stream().findFirst();
    }

    /** Minimal backend-only comparison context; applicant names are never copied into provider input. */
    public Optional<AnalysisApplicationContext> analysisApplicationContext(long applicationId) {
        return jdbc.query("""
                SELECT o.nome, o.cnpj, a.representation_type, u.nome AS applicant_legal_name,
                       u.nome_social AS applicant_social_name
                  FROM public.ngo_representative_verification_applications a
                  JOIN public.oscs o ON o.id=a.osc_id
                  LEFT JOIN public.usuarios u ON u.id=a.applicant_user_id
                 WHERE a.id=?
                """, (rs, row) -> new AnalysisApplicationContext(
                rs.getString("nome"), rs.getString("cnpj"), rs.getString("representation_type"),
                rs.getString("applicant_legal_name"), rs.getString("applicant_social_name")), applicationId)
                .stream().findFirst();
    }

    public Instant markSubmitted(long applicationId, int version) {
        Timestamp submitted = jdbc.queryForObject("""
                UPDATE public.ngo_representative_verification_submissions
                   SET submitted_at=clock_timestamp()
                 WHERE application_id=? AND version_number=? AND submitted_at IS NULL
                RETURNING submitted_at
                """, Timestamp.class, applicationId, version);
        if (submitted == null) throw new IllegalStateException("Não foi possível registrar o envio da documentação.");
        return submitted.toInstant();
    }

    public void insertEvidence(long submissionId, String type, String storageKey,
                               String mediaType, long sizeBytes, int uploadedByUserId) {
        jdbc.update("""
                INSERT INTO public.ngo_representative_verification_evidence
                    (submission_id, document_type, storage_key, media_type, size_bytes, uploaded_by_user_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, submissionId, type, storageKey, mediaType, sizeBytes, uploadedByUserId);
    }

    public int evidenceCount(long submissionId, String type) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM public.ngo_representative_verification_evidence
                 WHERE submission_id=? AND document_type=?
                """, Integer.class, submissionId, type);
        return count == null ? 0 : count;
    }

    public List<EvidenceRow> listEvidence(long applicationId, int version) {
        return jdbc.query("""
                SELECT e.id, e.document_type, e.media_type, e.size_bytes, e.uploaded_at,
                       e.disposal_status, e.disposal_requested_at, e.disposed_at
                  FROM public.ngo_representative_verification_evidence e
                  JOIN public.ngo_representative_verification_submissions s ON s.id=e.submission_id
                 WHERE s.application_id=? AND s.version_number=?
                 ORDER BY e.uploaded_at NULLS LAST, e.id
                """, EVIDENCE_ROW, applicationId, version);
    }

    /** Loads only active evidence attached to the exact submitted version for an internal worker. */
    public List<AnalysisEvidenceRow> listActiveAnalysisEvidence(long applicationId, long submissionId, int version) {
        return jdbc.query("""
                SELECT e.id, e.document_type, e.media_type, e.size_bytes, e.storage_key
                  FROM public.ngo_representative_verification_evidence e
                  JOIN public.ngo_representative_verification_submissions s ON s.id=e.submission_id
                 WHERE s.id=? AND s.application_id=? AND s.version_number=?
                   AND s.submitted_at IS NOT NULL AND e.disposal_status='ACTIVE'
                   AND e.storage_key IS NOT NULL AND e.media_type IS NOT NULL AND e.size_bytes IS NOT NULL
                 ORDER BY e.id
                """, (rs, row) -> new AnalysisEvidenceRow(rs.getLong("id"), rs.getString("document_type"),
                rs.getString("media_type"), rs.getLong("size_bytes"), rs.getString("storage_key")),
                submissionId, applicationId, version);
    }

    public List<EvidenceHistoryRow> listEvidenceHistory(long applicationId) {
        return jdbc.query("""
                SELECT s.version_number, s.submitted_at, e.id, e.document_type, e.media_type,
                       e.size_bytes, e.uploaded_at, e.disposal_status, e.disposal_requested_at, e.disposed_at
                  FROM public.ngo_representative_verification_submissions s
                  LEFT JOIN public.ngo_representative_verification_evidence e ON e.submission_id=s.id
                 WHERE s.application_id=?
                 ORDER BY s.version_number, e.uploaded_at NULLS LAST, e.id
                """, (rs, row) -> new EvidenceHistoryRow(
                rs.getInt("version_number"), instant(rs, "submitted_at"),
                rs.getObject("id") == null ? null : rs.getLong("id"), rs.getString("document_type"),
                rs.getString("media_type"), rs.getObject("size_bytes") == null ? null : rs.getLong("size_bytes"),
                instant(rs, "uploaded_at"), rs.getString("disposal_status"),
                instant(rs, "disposal_requested_at"), instant(rs, "disposed_at")), applicationId);
    }

    public Optional<String> evidenceStorageKey(long applicationId, long evidenceId, int version) {
        return jdbc.query("""
                SELECT e.storage_key
                  FROM public.ngo_representative_verification_evidence e
                  JOIN public.ngo_representative_verification_submissions s ON s.id=e.submission_id
                 WHERE s.application_id=? AND s.version_number=? AND e.id=?
                   AND e.disposal_status='ACTIVE' AND e.storage_key IS NOT NULL
                 FOR SHARE OF e
                """, (rs, row) -> rs.getString(1), applicationId, version, evidenceId).stream().findFirst();
    }

    public DisposalTicket requestEvidenceDisposal(long evidenceId, int actorUserId, String justification) {
        return jdbc.queryForObject("SELECT * FROM public.request_ngo_verification_evidence_disposal(?,?,?)",
                (rs, row) -> new DisposalTicket(rs.getLong("disposal_event_id"), rs.getString("object_key")),
                evidenceId, actorUserId, justification);
    }

    public long approveRetentionPolicy(int actorUserId, String approvalReference, String justification) {
        Long id = jdbc.queryForObject("SELECT public.approve_ngo_verification_retention_policy(?,?,?)",
                Long.class, actorUserId, approvalReference, justification);
        if (id == null) throw new IllegalStateException("A aprovação da retenção não foi registrada.");
        return id;
    }

    public void completeEvidenceDisposal(long evidenceId, long requestEventId, int actorUserId) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT public.complete_ngo_verification_evidence_disposal(?,?,?)")) {
                statement.setLong(1, evidenceId);
                statement.setLong(2, requestEventId);
                statement.setInt(3, actorUserId);
                statement.execute();
            }
            return null;
        });
    }

    public void recordEvidenceDisposalFailure(long evidenceId, long requestEventId, int actorUserId) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT public.record_ngo_verification_evidence_disposal_failure(?,?,?)")) {
                statement.setLong(1, evidenceId);
                statement.setLong(2, requestEventId);
                statement.setInt(3, actorUserId);
                statement.execute();
            }
            return null;
        });
    }

    public Optional<String> applicantFeedback(long applicationId) {
        return jdbc.query("""
                SELECT e.justification
                  FROM public.ngo_representative_verification_applications a
                  JOIN LATERAL (
                    SELECT justification FROM public.ngo_representative_verification_events
                     WHERE application_id=a.id
                       AND action=CASE a.status WHEN 'INFORMATION_REQUESTED' THEN 'INFORMATION_REQUESTED'
                                                WHEN 'REJECTED' THEN 'REJECTED' END
                       AND justification IS NOT NULL
                     ORDER BY created_at DESC, id DESC LIMIT 1
                  ) e ON TRUE
                 WHERE a.id=?
                """, (rs, row) -> rs.getString(1), applicationId).stream().findFirst();
    }

    public void updateStatus(long applicationId, String status) {
        jdbc.update("""
                UPDATE public.ngo_representative_verification_applications
                   SET status=?, updated_at=clock_timestamp()
                 WHERE id=?
                """, status, applicationId);
    }

    public long countAdminReopenEvents(long applicationId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM public.ngo_representative_verification_events
                 WHERE application_id=? AND action='ADMIN_REOPENED'
                """, Long.class, applicationId);
        return count == null ? 0 : count;
    }

    public void insertEvent(long applicationId, int actorUserId, String action,
                            String fromStatus, String toStatus, String justification) {
        String actorType = List.of("APPLICATION_CREATED", "REAPPLICATION_CREATED", "SUBMITTED", "RESUBMITTED")
                .contains(action) ? "APPLICANT" : "ADMINISTRATOR";
        jdbc.update("""
                INSERT INTO public.ngo_representative_verification_events
                    (application_id, actor_user_id, actor_type_snapshot, action, from_status, to_status, justification)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, applicationId, actorUserId, actorType, action, fromStatus, toStatus, justification);
    }

    public List<QueueRow> queue(String status, int limit, int offset) {
        String statusFilter = status == null ? "" : " AND a.status = ?";
        Object[] args = status == null
                ? new Object[]{limit, offset}
                : new Object[]{status, limit, offset};
        return jdbc.query("""
                SELECT a.id, a.osc_id, o.nome, a.status, a.representation_type,
                       a.current_submission_version, s.submitted_at
                  FROM public.ngo_representative_verification_applications a
                  JOIN public.oscs o ON o.id=a.osc_id
                  JOIN public.ngo_representative_verification_submissions s
                    ON s.application_id=a.id AND s.version_number=a.current_submission_version
                 WHERE s.submitted_at IS NOT NULL
                """ + statusFilter + " ORDER BY s.submitted_at, a.id LIMIT ? OFFSET ?", QUEUE_ROW, args);
    }

    public long countQueue(String status) {
        String sql = """
                SELECT count(*)
                  FROM public.ngo_representative_verification_applications a
                  JOIN public.ngo_representative_verification_submissions s
                    ON s.application_id=a.id AND s.version_number=a.current_submission_version
                 WHERE s.submitted_at IS NOT NULL
                """ + (status == null ? "" : " AND a.status=?");
        Long count = status == null ? jdbc.queryForObject(sql, Long.class)
                : jdbc.queryForObject(sql, Long.class, status);
        return count == null ? 0 : count;
    }

    public Optional<ReviewRow> findReviewRow(long applicationId) {
        return jdbc.query("""
                SELECT a.id, a.osc_id, o.nome, a.applicant_user_id, a.representation_type,
                       a.status, a.current_submission_version, a.created_at, s.submitted_at
                  FROM public.ngo_representative_verification_applications a
                  JOIN public.oscs o ON o.id=a.osc_id
                  LEFT JOIN public.ngo_representative_verification_submissions s
                    ON s.application_id=a.id AND s.version_number=a.current_submission_version
                 WHERE a.id=?
                """, REVIEW_ROW, applicationId).stream().findFirst();
    }

    public record ApplicationRow(long id, int oscId, Integer applicantUserId, String representationType,
                                 String status, int currentVersion, int applicationNumber,
                                 Long parentApplicationId, Instant createdAt) {
        public boolean applicantUserIdEquals(Integer userId) {
            return userId != null && Objects.equals(applicantUserId, userId);
        }
    }
    public record SubmissionRow(long id, long applicationId, int version, Instant submittedAt) { }
    public record AnalysisEvidenceRow(long id, String documentType, String mediaType, long sizeBytes, String storageKey) {
        @Override public String toString() {
            return "AnalysisEvidenceRow[id=" + id + ", documentType=" + documentType + ", mediaType=" + mediaType
                    + ", sizeBytes=" + sizeBytes + ", storageKey=[REDACTED]]";
        }
    }
    public record AnalysisApplicationContext(String organizationName, String organizationCnpj,
                                             String representationType, String applicantLegalName,
                                             String applicantSocialName) {
        public AnalysisApplicationContext(String organizationName, String organizationCnpj, String representationType) {
            this(organizationName, organizationCnpj, representationType, null, null);
        }

        @Override public String toString() {
            return "AnalysisApplicationContext[organizationName=" + organizationName + ", organizationCnpj=[REDACTED],"
                    + " representationType=" + representationType + ", applicantNames=[REDACTED]]";
        }
    }
    public record EvidenceRow(long id, String type, String mediaType, Long sizeBytes, Instant uploadedAt,
                              String disposalStatus, Instant disposalRequestedAt, Instant disposedAt) { }
    public record EvidenceHistoryRow(int version, Instant submittedAt, Long id, String type,
                                     String mediaType, Long sizeBytes, Instant uploadedAt,
                                     String disposalStatus, Instant disposalRequestedAt, Instant disposedAt) { }
    public record DisposalTicket(long eventId, String storageKey) { }
    public record QueueRow(long id, int oscId, String organizationName, String status,
                           String representationType, int currentVersion, Instant submittedAt) { }
    public record ReviewRow(long id, int oscId, String organizationName, Integer applicantUserId,
                            String representationType, String status, int currentVersion,
                            Instant createdAt, Instant submittedAt) { }

    private static final RowMapper<ApplicationRow> APPLICATION_ROW = (rs, row) -> new ApplicationRow(
            rs.getLong("id"), rs.getInt("osc_id"), rs.getObject("applicant_user_id", Integer.class),
            rs.getString("representation_type"), rs.getString("status"), rs.getInt("current_submission_version"),
            rs.getInt("application_number"), (Long) rs.getObject("parent_application_id"), instant(rs, "created_at"));
    private static final RowMapper<SubmissionRow> SUBMISSION_ROW = (rs, row) -> new SubmissionRow(
            rs.getLong("id"), rs.getLong("application_id"), rs.getInt("version_number"), instant(rs, "submitted_at"));
    private static final RowMapper<EvidenceRow> EVIDENCE_ROW = (rs, row) -> new EvidenceRow(
            rs.getLong("id"), rs.getString("document_type"), rs.getString("media_type"),
            rs.getObject("size_bytes") == null ? null : rs.getLong("size_bytes"), instant(rs, "uploaded_at"),
            rs.getString("disposal_status"), instant(rs, "disposal_requested_at"), instant(rs, "disposed_at"));
    private static final RowMapper<QueueRow> QUEUE_ROW = (rs, row) -> new QueueRow(
            rs.getLong("id"), rs.getInt("osc_id"), rs.getString("nome"), rs.getString("status"),
            rs.getString("representation_type"), rs.getInt("current_submission_version"), instant(rs, "submitted_at"));
    private static final RowMapper<ReviewRow> REVIEW_ROW = (rs, row) -> new ReviewRow(
            rs.getLong("id"), rs.getInt("osc_id"), rs.getString("nome"), rs.getObject("applicant_user_id", Integer.class),
            rs.getString("representation_type"), rs.getString("status"),
            rs.getInt("current_submission_version"), instant(rs, "created_at"), instant(rs, "submitted_at"));

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
