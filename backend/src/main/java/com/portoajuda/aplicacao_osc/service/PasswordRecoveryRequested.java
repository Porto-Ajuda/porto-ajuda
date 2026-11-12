package com.portoajuda.aplicacao_osc.service;

/** Evento transitório, sem representação textual para evitar registrar a credencial. */
public final class PasswordRecoveryRequested {
    private final String email;
    private final String token;

    public PasswordRecoveryRequested(String email, String token) {
        this.email = email;
        this.token = token;
    }

    public String email() { return email; }
    public String token() { return token; }

    @Override
    public String toString() { return "PasswordRecoveryRequested[redacted]"; }
}
