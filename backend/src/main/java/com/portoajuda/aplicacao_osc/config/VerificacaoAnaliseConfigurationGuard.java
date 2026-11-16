package com.portoajuda.aplicacao_osc.config;

import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fail closed if an operator enables analysis before a provider and the additive schema are installed. */
@Component
public class VerificacaoAnaliseConfigurationGuard implements ApplicationRunner {
    private final boolean analysisEnabled;
    private final boolean autoApprovalEnabled;
    private final boolean verificationEnabled;
    private final boolean generalWorkerEnabled;
    private final boolean evaluationWorkerEnabled;
    private final String providerName;
    private final String storageSecret;
    private final String geminiApiKey;
    private final ObjectProvider<AnaliseRepresentacaoDocumental> provider;
    private final JdbcTemplate jdbc;

    @Autowired
    public VerificacaoAnaliseConfigurationGuard(
            @Value("${app.ngo-verification.ai-analysis.enabled:false}") boolean analysisEnabled,
            @Value("${app.ngo-verification.ai-analysis.worker-enabled:false}") boolean generalWorkerEnabled,
            @Value("${app.ngo-verification.ai-analysis.evaluation-worker-enabled:false}") boolean evaluationWorkerEnabled,
            @Value("${app.ngo-verification.auto-approval.enabled:false}") boolean autoApprovalEnabled,
            @Value("${app.ngo-verification.enabled:false}") boolean verificationEnabled,
            @Value("${app.ngo-verification.ai-analysis.provider:none}") String providerName,
            @Value("${supabase.secret-key:}") String storageSecret,
            @Value("${gemini.api-key:}") String geminiApiKey,
            ObjectProvider<AnaliseRepresentacaoDocumental> provider,
            JdbcTemplate jdbc) {
        this.analysisEnabled = analysisEnabled;
        this.generalWorkerEnabled = generalWorkerEnabled;
        this.evaluationWorkerEnabled = evaluationWorkerEnabled;
        this.autoApprovalEnabled = autoApprovalEnabled;
        this.verificationEnabled = verificationEnabled;
        this.providerName = providerName;
        this.storageSecret = storageSecret;
        this.geminiApiKey = geminiApiKey;
        this.provider = provider;
        this.jdbc = jdbc;
    }

    public VerificacaoAnaliseConfigurationGuard(boolean analysisEnabled, boolean autoApprovalEnabled,
                                         boolean verificationEnabled, String providerName,
                                         String storageSecret, String geminiApiKey,
                                         ObjectProvider<AnaliseRepresentacaoDocumental> provider, JdbcTemplate jdbc) {
        this(analysisEnabled, false, false, autoApprovalEnabled, verificationEnabled,
                providerName, storageSecret, geminiApiKey, provider, jdbc);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (autoApprovalEnabled) {
            throw new IllegalStateException("Automated NGO verification approval is not implemented; keep its flag disabled.");
        }
        if (generalWorkerEnabled && evaluationWorkerEnabled) {
            throw new IllegalStateException("General and evaluation workers cannot run together.");
        }
        if (!analysisEnabled) return;
        if (!verificationEnabled) {
            throw new IllegalStateException("AI analysis requires NGO verification to be enabled in the same process.");
        }
        if (providerName == null || providerName.isBlank() || "none".equalsIgnoreCase(providerName)
                || provider.getIfAvailable() == null) {
            throw new IllegalStateException("AI analysis is enabled without a configured provider adapter.");
        }
        if (storageSecret == null || storageSecret.isBlank()) {
            throw new IllegalStateException("AI analysis requires the existing private Storage service credential.");
        }
        if ("gemini".equalsIgnoreCase(providerName) && (geminiApiKey == null || geminiApiKey.isBlank())) {
            throw new IllegalStateException("Gemini document analysis is enabled without an API credential.");
        }
        Boolean jobTablesExist = jdbc.queryForObject("""
                SELECT to_regclass('public.ngo_representative_verification_ai_jobs') IS NOT NULL
                   AND to_regclass('public.ngo_representative_verification_ai_job_events') IS NOT NULL
                """, Boolean.class);
        if (!Boolean.TRUE.equals(jobTablesExist)) {
            throw new IllegalStateException("AI analysis job schema is not installed; do not enqueue analysis jobs.");
        }
    }
}
