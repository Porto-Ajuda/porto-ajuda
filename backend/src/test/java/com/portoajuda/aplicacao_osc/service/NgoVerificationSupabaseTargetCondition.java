package com.portoajuda.aplicacao_osc.service;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Fail-closed guard for the explicitly authorized project Supabase TEST database. */
public final class NgoVerificationSupabaseTargetCondition implements ExecutionCondition {
    private static final Pattern JDBC = Pattern.compile(
            "^jdbc:postgresql://([^/:?]+)(?::[0-9]+)?/([^?]+)");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!"true".equalsIgnoreCase(System.getenv("NGO_VERIFICATION_SUPABASE_TEST_CONFIRMED"))) {
            return ConditionEvaluationResult.disabled("Supabase TEST integration was not explicitly enabled.");
        }
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String api = System.getenv("SUPABASE_URL");
        Matcher parsed = url == null ? null : JDBC.matcher(url);
        if (parsed == null || !parsed.find() || !"postgres".equals(parsed.group(2))
                || user == null || api == null) {
            return ConditionEvaluationResult.disabled("The explicit TEST datasource identity is incomplete.");
        }
        String host = parsed.group(1);
        String apiHost;
        try { apiHost = URI.create(api).getHost(); }
        catch (RuntimeException invalid) { return ConditionEvaluationResult.disabled("SUPABASE_URL is invalid."); }
        String projectRef = apiHost == null ? "" : apiHost.split("\\.")[0];
        boolean direct = host.replaceFirst("^db\\.", "").equalsIgnoreCase(apiHost);
        boolean pooler = host.endsWith(".pooler.supabase.com")
                && user.matches(".*\\." + Pattern.quote(projectRef) + "$");
        if (!(direct || pooler) || !(host.endsWith(".supabase.com") || host.endsWith(".supabase.co"))) {
            return ConditionEvaluationResult.disabled("DB_URL and SUPABASE_URL do not match the authorized TEST project.");
        }
        return ConditionEvaluationResult.enabled("Explicitly confirmed Supabase TEST target matches the API project.");
    }
}
