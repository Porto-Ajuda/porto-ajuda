package com.portoajuda.aplicacao_osc.service;

import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Requires an explicitly confirmed, task-created loopback PostgreSQL database; never reads .env. */
public final class NgoVerificationTestDatabaseCondition implements ExecutionCondition {
    private static final Pattern URL = Pattern.compile(
            "^jdbc:postgresql://127\\.0\\.0\\.1:55447/ngo_representative_verification_test(?:\\?.*)?$");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!"true".equalsIgnoreCase(System.getenv("NGO_VERIFICATION_TEST_DATABASE_CONFIRMED"))) {
            return ConditionEvaluationResult.disabled("Disposable NGO verification database was not explicitly confirmed.");
        }
        String url = System.getenv("DB_URL");
        if (url == null || !URL.matcher(url).matches()
                || blank(System.getenv("DB_USER")) || blank(System.getenv("DB_PASSWORD"))) {
            return ConditionEvaluationResult.disabled(
                    "The datasource must be explicitly supplied for 127.0.0.1:55447/ngo_representative_verification_test.");
        }
        return ConditionEvaluationResult.enabled("Explicit task-created disposable PostgreSQL target confirmed.");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
