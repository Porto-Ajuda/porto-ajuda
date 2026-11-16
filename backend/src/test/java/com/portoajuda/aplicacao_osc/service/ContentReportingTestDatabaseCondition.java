package com.portoajuda.aplicacao_osc.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Fails closed before Spring creates a datasource unless the operator confirms the exact test host. */
public final class ContentReportingTestDatabaseCondition implements ExecutionCondition {
    private static final Pattern JDBC_URL = Pattern.compile("^jdbc:postgresql://([^:/?]+)(?::([0-9]+))?/([^?]+)");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!"true".equalsIgnoreCase(System.getenv("CONTENT_REPORTING_TEST_DATABASE_CONFIRMED"))) {
            return ConditionEvaluationResult.disabled("Explicit content-reporting TEST database confirmation is absent.");
        }
        String expectedHost = System.getenv("CONTENT_REPORTING_TEST_DB_HOST");
        String url = System.getenv("DB_URL");
        if (url == null || url.isBlank()) url = localDotEnvValue("DB_URL");
        if (expectedHost == null || expectedHost.isBlank() || url == null) {
            return ConditionEvaluationResult.disabled("A confirmed TEST host and effective DB_URL are required.");
        }
        Matcher matcher = JDBC_URL.matcher(url);
        if (!matcher.find() || !expectedHost.equalsIgnoreCase(matcher.group(1))
                || !"postgres".equals(matcher.group(3))
                || !(matcher.group(1).endsWith(".supabase.com") || matcher.group(1).endsWith(".supabase.co"))) {
            return ConditionEvaluationResult.disabled("Effective datasource does not match the explicitly confirmed Supabase TEST target.");
        }
        return ConditionEvaluationResult.enabled("Explicit Supabase TEST host confirmation matches the effective datasource.");
    }

    private String localDotEnvValue(String key) {
        Path directory = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; directory != null && depth < 5; depth++, directory = directory.getParent()) {
            Path file = directory.resolve(".env");
            if (!Files.isRegularFile(file)) continue;
            try {
                for (String line : Files.readAllLines(file)) {
                    if (!line.matches("^\\s*" + Pattern.quote(key) + "\\s*=.*$")) continue;
                    return line.replaceFirst("^\\s*" + Pattern.quote(key) + "\\s*=", "")
                            .trim().replaceAll("^['\"]|['\"]$", "");
                }
            } catch (IOException ignored) {
                return null;
            }
        }
        return null;
    }
}
