package com.portoajuda.aplicacao_osc.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminReasonSanitizerTest {

    @Test
    void removeCookieBearerTokenJwtOtpEChaveSemApagarContextoDaJustificativa() {
        String input = "Revisão de caso PA-1234; PA_ADMIN_SESSION=session-secret-value; "
                + "Authorization: Bearer bearer-secret-value; OTP=123456; "
                + "JWT eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signaturelong; "
                + "opaque-credential-123456789012345678901234567890123456";

        String sanitized = AdminReasonSanitizer.sanitize(input);

        assertTrue(sanitized.contains("Revisão de caso PA-1234"));
        assertFalse(sanitized.contains("session-secret-value"));
        assertFalse(sanitized.contains("bearer-secret-value"));
        assertFalse(sanitized.contains("123456"));
        assertFalse(sanitized.contains("eyJhbGci"));
        assertFalse(sanitized.contains("opaque-credential-123456789012345678901234567890123456"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }
}
