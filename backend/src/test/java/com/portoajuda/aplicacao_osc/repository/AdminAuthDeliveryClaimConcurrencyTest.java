package com.portoajuda.aplicacao_osc.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class AdminAuthDeliveryClaimConcurrencyTest {

    @Test
    void claimLocksChallengeAndSkipsSiblingWhileAnotherFactorIsProcessing() throws Exception {
        Query query = AdminAuthDeliveryRepository.class
                .getMethod("claimNextPending")
                .getAnnotation(Query.class);

        assertTrue(query.value().contains("FOR UPDATE OF c, d SKIP LOCKED"));
        assertTrue(query.value().contains("active.challenge_id = c.id"));
        assertTrue(query.value().contains("active.state = 'PROCESSING'"));
        assertTrue(query.value().contains("active.lease_until > CURRENT_TIMESTAMP"));
    }
}
