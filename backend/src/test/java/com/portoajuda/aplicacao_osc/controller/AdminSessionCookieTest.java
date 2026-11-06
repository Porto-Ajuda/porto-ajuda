package com.portoajuda.aplicacao_osc.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AdminSessionCookieTest {

    @Test
    void cookieDeSessaoEHttpOnlySecureSameSiteNoneERestritoAAdmin() {
        String header = new AdminSessionCookie(true).issue("test-opaque-value", Duration.ofHours(2)).toString();

        assertTrue(header.startsWith("PA_ADMIN_SESSION=test-opaque-value"));
        assertTrue(header.contains("Path=/admin"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=None"));
        assertTrue(header.contains("Max-Age=7200"));
    }

    @Test
    void logoutLimpaMesmoCookieComMesmoPathESecureFlags() {
        String header = new AdminSessionCookie(true).clear().toString();

        assertTrue(header.startsWith("PA_ADMIN_SESSION="));
        assertTrue(header.contains("Path=/admin"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=None"));
        assertTrue(header.contains("Max-Age=0"));
    }
}
