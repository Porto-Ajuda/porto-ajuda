package com.portoajuda.aplicacao_osc.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Requires explicit operator confirmation and the same Supabase TEST project as SUPABASE_URL. */
public final class NgoVerificationSupabaseTestDatabaseCondition implements ExecutionCondition {
    private static final Pattern JDBC = Pattern.compile(
            "^jdbc:postgresql://([^/:?]+)(?::[0-9]+)?/([^?]+)");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!"true".equalsIgnoreCase(System.getenv("NGO_VERIFICATION_SUPABASE_TEST_CONFIRMED"))) {
            return ConditionEvaluationResult.disabled("Explicit Supabase TEST confirmation is absent.");
        }
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String api = System.getenv("SUPABASE_URL");
        if (url == null || user == null || api == null || !"postgres".equals(user)) {
            return ConditionEvaluationResult.disabled("Supabase TEST datasource environment is incomplete.");
        }
        Matcher jdbc = JDBC.matcher(url);
        if (!jdbc.find() || !"postgres".equals(jdbc.group(2))) {
            return ConditionEvaluationResult.disabled("DB_URL must identify the postgres database.");
        }
        String host = jdbc.group(1);
        String apiHost;
        try { apiHost = java.net.URI.create(api).getHost(); }
        catch (RuntimeException invalid) { return ConditionEvaluationResult.disabled("SUPABASE_URL is invalid."); }
        String projectRef = apiHost == null ? "" : apiHost.split("\\.")[0];
        boolean direct = host.replaceFirst("^db\\.", "").equalsIgnoreCase(apiHost);
        boolean pooler = host.endsWith(".pooler.supabase.com")
                && user.matches(".*(?:^|\\.)" + Pattern.quote(projectRef) + "$");
        if (!(direct || pooler) || !(host.endsWith(".supabase.com") || host.endsWith(".supabase.co"))) {
            return ConditionEvaluationResult.disabled("Datasource does not match SUPABASE_URL project identity.");
        }
        return ConditionEvaluationResult.enabled("Explicit Supabase TEST datasource confirmation matches.");
    }
}
