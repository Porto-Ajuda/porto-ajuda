package com.portoajuda.aplicacao_osc.controller;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AdminSessionCookie {

    public static final String NAME = "PA_ADMIN_SESSION";
    public static final String PATH = "/admin";

    private final boolean secureCookies;

    public AdminSessionCookie(@Value("${app.cookies.secure:true}") boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    public ResponseCookie issue(String rawCredential, Duration maxAge) {
        return ResponseCookie.from(NAME, rawCredential)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(secureCookies ? "None" : "Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clear() {
        return issue("", Duration.ZERO);
    }
}
