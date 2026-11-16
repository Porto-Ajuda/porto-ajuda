package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Read-only, aggregate-only inventory. Does not start application workers or expose job identifiers. */
@ExtendWith(VerificacaoAiQueueInventorySupabaseTest.ConfirmedProjectTestDatabase.class)
class VerificacaoAiQueueInventorySupabaseTest {
    @Test
    void reportsQueueStatusCountsWithoutClaimingOrMutatingJobs() throws Exception {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        assertTrue(url != null && user != null && password != null);
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT status, count(*)
                       FROM public.ngo_representative_verification_ai_jobs
                      GROUP BY status ORDER BY status
                     """)) {
            while (result.next()) {
                // Only aggregate state counts are emitted; never print IDs, application data, or result summaries.
                System.out.printf("QUEUE_STATUS %s %d%n", result.getString(1), result.getLong(2));
            }
        }
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT count(*) FROM public.ngo_representative_verification_ai_jobs
                      WHERE status IN ('QUEUED','RETRY','PROCESSING')
                     """)) {
            assertTrue(result.next());
            System.out.printf("QUEUE_ACTIONABLE_TOTAL %d%n", result.getLong(1));
        }
    }

    @Test
    void readsOnlySafeMetadataForOneExplicitEvaluationJob() throws Exception {
        long jobId = Long.getLong("ngo.ai.read-only-job-id", -1L);
        Assumptions.assumeTrue(jobId > 0, "No exact task-owned job ID was supplied for read-only inspection.");
        String url = System.getenv("DB_URL");
        try (var connection = DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT j.status,j.attempts,j.input_tokens,j.output_tokens,j.result_summary::text,
                            a.status AS application_status,a.current_submission_version,s.version_number
                       FROM public.ngo_representative_verification_ai_jobs j
                       JOIN public.ngo_representative_verification_applications a ON a.id=j.application_id
                       JOIN public.ngo_representative_verification_submissions s ON s.id=j.submission_id
                      WHERE j.id=?
                     """)) {
            statement.setLong(1, jobId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next(), "Exact scoped job must persist.");
                String status = result.getString(1);
                int attempts = result.getInt(2);
                Integer inputTokens = (Integer) result.getObject(3);
                Integer outputTokens = (Integer) result.getObject(4);
                String json = result.getString(5);
                var summary = new ObjectMapper().readTree(json);
                String appStatus = result.getString(6);
                int jobVersion = result.getInt(8);
                int currentVersion = result.getInt(7);
                assertEquals("COMPLETED", status);
                assertEquals(1, attempts);
                assertEquals("PENDING", appStatus);
                assertEquals(currentVersion, jobVersion);
                assertEquals("INSUFFICIENT_EVIDENCE", summary.path("identityComparison").asText());
                assertTrue(summary.path("discrepancies").toString().contains("REPRESENTANTE_LEGAL_NAO_IDENTIFICADO"));
                assertEquals("DIVERGENCIA", summary.path("conclusion").asText());
                java.util.List<String> fieldStates = new java.util.ArrayList<>();
                java.util.Map<String, String> fields = new java.util.HashMap<>();
                summary.path("fields").forEach(field -> {
                    String name = field.path("field").asText();
                    String state = field.path("status").asText();
                    fieldStates.add(name + ":" + state);
                    fields.put(name, state);
                });
                assertEquals("ENCONTRADO", fields.get("NOME_ENTIDADE"));
                assertEquals("ENCONTRADO", fields.get("CNPJ"));
                assertEquals("AUSENTE", fields.get("REPRESENTANTE_LEGAL"));
                assertEquals("INCERTO", fields.get("PODERES_REPRESENTACAO"),
                        "The model uncertainty about authority is retained for manual review.");
                System.out.printf("EVAL_JOB_SAFE status=%s attempts=%d inputTokens=%d outputTokens=%d appStatus=%s jobVersion=%d currentVersion=%d conclusion=%s identity=%s delegation=%s validity=%s discrepancies=%s%n",
                        status, attempts, inputTokens, outputTokens, appStatus,
                        jobVersion, currentVersion, summary.path("conclusion").asText(),
                        summary.path("identityComparison").asText(),
                        summary.path("delegationEvidenceChain").asText(),
                        summary.path("apparentValidity").asText(), summary.path("discrepancies").toString());
                System.out.printf("EVAL_JOB_FIELD_STATES %s%n", fieldStates);
                assertFalse(result.next(), "Job ID must identify exactly one durable row.");
            }
        }
        try (var connection = DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT event_type,count(*) FROM public.ngo_representative_verification_ai_job_events
                      WHERE job_id=? GROUP BY event_type ORDER BY event_type
                     """)) {
            statement.setLong(1, jobId);
            try (var result = statement.executeQuery()) {
                java.util.Map<String, Long> events = new java.util.HashMap<>();
                while (result.next()) events.put(result.getString(1), result.getLong(2));
                assertEquals(1L, events.get("QUEUED"));
                assertEquals(1L, events.get("CLAIMED"));
                assertEquals(1L, events.get("COMPLETED"));
                System.out.printf("EVAL_JOB_EVENTS queued=%d claimed=%d completed=%d%n",
                        events.get("QUEUED"), events.get("CLAIMED"), events.get("COMPLETED"));
            }
        }
    }

    @Test
    void verifiesOneTaskOwnedSyntheticApplicantWasDeactivatedAndHistoryWasRetained() throws Exception {
        int userId = Integer.getInteger("ngo.ai.fixture-user-id", -1);
        long applicationId = Long.getLong("ngo.ai.fixture-application-id", -1L);
        Assumptions.assumeTrue(userId > 0 && applicationId > 0,
                "No exact task-owned synthetic fixture IDs were supplied.");
        String url = System.getenv("DB_URL");
        try (var connection = DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT u.ativo,a.status,
                            (SELECT count(*) FROM public.ngo_representative_verification_ai_jobs j WHERE j.application_id=a.id),
                            (SELECT count(*) FROM public.ngo_representative_verification_submissions s WHERE s.application_id=a.id),
                            (SELECT count(*) FROM public.ngo_representative_verification_evidence e
                              JOIN public.ngo_representative_verification_submissions s ON s.id=e.submission_id
                             WHERE s.application_id=a.id)
                       FROM public.ngo_representative_verification_applications a
                       JOIN public.usuarios u ON u.id=a.applicant_user_id
                      WHERE a.id=? AND u.id=?
                     """)) {
            statement.setLong(1, applicationId);
            statement.setInt(2, userId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next(), "Synthetic application and applicant must remain as an auditable record.");
                boolean active = result.getBoolean(1);
                String status = result.getString(2);
                long jobs = result.getLong(3);
                long submissions = result.getLong(4);
                long evidence = result.getLong(5);
                assertFalse(active, "Only the synthetic applicant account is deactivated after evaluation.");
                assertEquals("PENDING", status, "No reviewer decision was made by AI evaluation.");
                assertEquals(1, jobs);
                assertEquals(1, submissions);
                assertEquals(1, evidence);
                System.out.printf("SYNTHETIC_FIXTURE retained=true applicantActive=%s applicationStatus=%s jobs=%d submissions=%d evidence=%d%n",
                        active, status, jobs, submissions, evidence);
                assertFalse(result.next());
            }
        }
    }

    @Test
    void listsRecentSyntheticAdminFixtureIdsWithoutContacts() throws Exception {
        String url = System.getenv("DB_URL");
        try (var connection = DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT u.id,u.ativo,a.id AS assignment_id,a.status AS assignment_status
                       FROM public.usuarios u
                       LEFT JOIN public.admin_assignments a ON a.usuario_id_snapshot=u.id
                      WHERE u.nome='Conta Sintética NGO Verification'
                        AND u.data_criacao >= CURRENT_TIMESTAMP - INTERVAL '15 minutes'
                      ORDER BY u.data_criacao,u.id
                     """)) {
            int rows = 0;
            while (result.next()) {
                rows++;
                System.out.printf("RECENT_SYNTHETIC_ADMIN_FIXTURE userId=%d active=%s assignmentId=%s assignmentStatus=%s%n",
                        result.getInt("id"), result.getBoolean("ativo"),
                        result.getObject("assignment_id"), result.getString("assignment_status"));
            }
            assertTrue(rows > 0, "The two recent integration attempts should leave auditable synthetic identities.");
        }
    }

    /** Read-only inventory allows the configured scoped runtime role, but only for the confirmed project. */
    static final class ConfirmedProjectTestDatabase implements ExecutionCondition {
        private static final Pattern JDBC = Pattern.compile("^jdbc:postgresql://([^/:?]+)(?::[0-9]+)?/([^?]+)");

        @Override public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (!"true".equalsIgnoreCase(System.getenv("NGO_VERIFICATION_SUPABASE_TEST_CONFIRMED"))) {
                return ConditionEvaluationResult.disabled("Explicit TEST project confirmation is absent.");
            }
            String url = System.getenv("DB_URL");
            String user = System.getenv("DB_USER");
            String api = System.getenv("SUPABASE_URL");
            if (url == null || user == null || user.isBlank() || api == null) {
                return ConditionEvaluationResult.disabled("TEST datasource configuration is incomplete.");
            }
            Matcher matcher = JDBC.matcher(url);
            if (!matcher.find() || !"postgres".equals(matcher.group(2))) {
                return ConditionEvaluationResult.disabled("Datasource must target the project's postgres database.");
            }
            try {
                String apiHost = URI.create(api).getHost();
                String projectRef = apiHost == null ? "" : apiHost.split("\\.")[0];
                String jdbcHost = matcher.group(1);
                boolean direct = jdbcHost.replaceFirst("^db\\.", "").equalsIgnoreCase(apiHost);
                boolean pooler = jdbcHost.endsWith(".pooler.supabase.com")
                        && user.matches(".*(?:^|\\.)" + Pattern.quote(projectRef) + "$");
                if (!direct && !pooler) {
                    return ConditionEvaluationResult.disabled("Datasource host does not match the configured project.");
                }
                return ConditionEvaluationResult.enabled("Explicit TEST project and datasource host match.");
            } catch (RuntimeException invalid) {
                return ConditionEvaluationResult.disabled("Supabase project URL is invalid.");
            }
        }
    }
}
