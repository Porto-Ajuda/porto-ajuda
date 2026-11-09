package com.portoajuda.aplicacao_osc.admin;

import java.util.UUID;

/** Session identifier attached only by AdminSessionAuthFilter after cookie validation. */
public record AdminAuthenticationDetails(UUID sessionId) {
}
