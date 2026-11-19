package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.AdminAuthDeliveryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthDeliveryFailureDiagnosticsTest {
    @Mock private AdminAuthDeliveryRepository deliveryRepository;
    @Mock private AdminAuthChallengeRepository challengeRepository;
    @Mock private AdminAuthCodeSecurity codeSecurity;
    @Mock private AdminAuthAuditService audit;
    @Mock private AdminAuthorizationService authorization;

    @Test
    void providerFailurePersistsCategoryAndStatusBeforeInvalidatingChallenge() {
        UUID challengeId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        AdminAuthChallenge challenge = new AdminAuthChallenge();
        challenge.setId(challengeId);
        challenge.setState(AdminAuthChallenge.State.ISSUING);
        challenge.setExpiresAt(Instant.now().plusSeconds(300));
        AdminAuthDelivery delivery = new AdminAuthDelivery();
        delivery.setId(deliveryId);
        delivery.setChallenge(challenge);
        delivery.setChannel(AdminAuthDelivery.Channel.SMS);
        delivery.setState(AdminAuthDelivery.State.PROCESSING);
        delivery.setAttemptCount(1);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        new AdminAuthDeliveryQueueService(
                deliveryRepository, challengeRepository, codeSecurity, audit, authorization)
                .finish(deliveryId, false, "SMS_PROVIDER_REJECTED", 503, false, null);

        assertEquals("SMS_PROVIDER_REJECTED", delivery.getFailureCategory());
        assertEquals(503, delivery.getProviderStatus());
        assertEquals(AdminAuthDelivery.State.FAILED, delivery.getState());
        assertEquals(AdminAuthChallenge.State.FAILED, challenge.getState());
        verify(deliveryRepository).saveAndFlush(delivery);
        verify(deliveryRepository).failChallengeDeliveries(
                eq(challengeId), anyList(), eq(AdminAuthDelivery.State.FAILED),
                eq("CHALLENGE_DELIVERY_FAILURE"), any(Instant.class));
    }
}
