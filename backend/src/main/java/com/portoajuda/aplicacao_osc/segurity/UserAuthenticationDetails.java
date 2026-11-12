package com.portoajuda.aplicacao_osc.segurity;

/** Session identifier attached only after the signed access token and stored session are verified. */
public record UserAuthenticationDetails(Long sessionId) {
}
