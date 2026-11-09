package com.portoajuda.aplicacao_osc.admin;

import java.util.regex.Pattern;

/** Removes credential-like fragments before an operator-supplied reason is persisted. */
public final class AdminReasonSanitizer {

    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(\\b(?:pa_admin_session|x-pa-admin-session|authorization|cookie"
                    + "|access[-_ ]?token|refresh[-_ ]?token|token|secret|password|passwd|senha"
                    + "|otp|mfa[-_ ]?code|verification[-_ ]?code|api[-_ ]?key)\\b\\s*[:=]\\s*)"
                    + "(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\b");
    private static final Pattern KNOWN_SECRET_PREFIX = Pattern.compile(
            "(?i)\\b(?:sk_(?:live|test)_|rk_(?:live|test)_|sb_secret_|sbp_)[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern OPAQUE_CREDENTIAL = Pattern.compile(
            "(?<![A-Za-z0-9])[A-Za-z0-9_./+~=:-]{24,}(?![A-Za-z0-9])");
    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private AdminReasonSanitizer() {
    }

    public static String sanitize(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        String sanitized = BEARER.matcher(reason).replaceAll("[REDACTED]");
        sanitized = NAMED_SECRET.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = JWT.matcher(sanitized).replaceAll("[REDACTED]");
        sanitized = KNOWN_SECRET_PREFIX.matcher(sanitized).replaceAll("[REDACTED]");
        sanitized = OPAQUE_CREDENTIAL.matcher(sanitized).replaceAll("[REDACTED]");
        sanitized = SIX_DIGIT_CODE.matcher(sanitized).replaceAll("[REDACTED]");
        return sanitized.trim();
    }
}
