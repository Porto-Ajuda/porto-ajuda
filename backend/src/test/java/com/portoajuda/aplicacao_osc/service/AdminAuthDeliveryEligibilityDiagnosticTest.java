package com.portoajuda.aplicacao_osc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.AdminAuthDeliveryRepository;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminAuthDeliveryEligibilityDiagnosticTest {

    private final AdminAuthDeliveryRepository deliveries = mock(AdminAuthDeliveryRepository.class);
    private final AdminAuthChallengeRepository challenges = mock(AdminAuthChallengeRepository.class);
    private final AdminAuthCodeSecurity crypto = mock(AdminAuthCodeSecurity.class);
    private final AdminAuthAuditService audit = mock(AdminAuthAuditService.class);
    private final AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);

    @Test
    void recordsChallengeStateRejectionWithoutProviderDataOrSecrets() {
        UUID challengeId = UUID.randomUUID();
        AdminAuthChallenge challenge = challenge(challengeId, AdminAuthChallenge.State.INVALIDATED);
        Usuario user = mock(Usuario.class);
        when(user.getId()).thenReturn(812345);
        challenge.setUsuario(user);
        when(challenges.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(deliveries.claimNextPending()).thenReturn(Optional.of(delivery(challengeId, challenge)));

        createQueue().claimNext();

        verify(audit).registrar(
                "ADMIN_AUTH_DELIVERY_INELIGIBLE_CHALLENGE_STATE_NOT_ISSUING",
                812345, challengeId, null, null);
        verify(audit).registrar(
                "ADMIN_AUTH_DELIVERY_NO_LONGER_ELIGIBLE",
                812345, challengeId, null, null);
        verify(deliveries).failChallengeDeliveries(any(), any(), any(), any());
        verifyNoInteractions(crypto);
    }

    @Test
    void recordsAssignmentEligibilitySeparatelyFromChallengeState() {
        UUID challengeId = UUID.randomUUID();
        AdminAuthChallenge challenge = challenge(challengeId, AdminAuthChallenge.State.ISSUING);
        Usuario user = mock(Usuario.class);
        when(user.getId()).thenReturn(812346);
        challenge.setUsuario(user);
        when(challenges.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(deliveries.claimNextPending()).thenReturn(Optional.of(delivery(challengeId, challenge)));
        when(authorization.privilegedSessionEligibility(user)).thenReturn(
                AdminAuthorizationService.PrivilegedSessionEligibility.ACTIVE_ASSIGNMENT_MISSING);

        createQueue().claimNext();

        verify(audit).registrar(
                "ADMIN_AUTH_DELIVERY_INELIGIBLE_ACTIVE_ASSIGNMENT_MISSING",
                812346, challengeId, null, null);
        verify(authorization).privilegedSessionEligibility(user);
        verify(crypto, never()).accountKey(any());
    }

    @Test
    void givesDistinctReasonCodesForEveryEligibilityAndSnapshotRejection() {
        AdminAuthDeliveryQueueService queue = createQueue();
        Usuario user = mock(Usuario.class);
        when(user.getEmail()).thenReturn(new Email("synthetic-admin@example.invalid"));
        when(user.getTelefone()).thenReturn("11987654321");
        AdminAuthChallenge challenge = challenge(UUID.randomUUID(), AdminAuthChallenge.State.ISSUING);

        assertEquals("CHALLENGE_MISSING", queue.eligibilityRejectionReason(null, user, Instant.now()));
        assertEquals("USER_MISSING", queue.eligibilityRejectionReason(challenge, null, Instant.now()));
        assertEquals("CHALLENGE_STATE_NOT_ISSUING", queue.eligibilityRejectionReason(
                challenge(challenge.getId(), AdminAuthChallenge.State.INVALIDATED), user, Instant.now()));
        AdminAuthChallenge expired = challenge(challenge.getId(), AdminAuthChallenge.State.ISSUING);
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        assertEquals("CHALLENGE_EXPIRED", queue.eligibilityRejectionReason(expired, user, Instant.now()));

        for (AdminAuthorizationService.PrivilegedSessionEligibility reason
                : AdminAuthorizationService.PrivilegedSessionEligibility.values()) {
            if (reason == AdminAuthorizationService.PrivilegedSessionEligibility.ELIGIBLE) continue;
            when(authorization.privilegedSessionEligibility(user)).thenReturn(reason);
            assertEquals(reason.name(), queue.eligibilityRejectionReason(challenge, user, Instant.now()));
        }

        when(authorization.privilegedSessionEligibility(user)).thenReturn(
                AdminAuthorizationService.PrivilegedSessionEligibility.ELIGIBLE);
        when(crypto.accountKey("synthetic-admin@example.invalid")).thenReturn("email-hmac");
        challenge.setEmailSnapshotHash("old-email-hmac");
        assertEquals("EMAIL_SNAPSHOT_MISMATCH", queue.eligibilityRejectionReason(challenge, user, Instant.now()));

        challenge.setEmailSnapshotHash("email-hmac");
        when(crypto.phoneKey("5511987654321")).thenReturn("phone-hmac");
        challenge.setPhoneSnapshotHash("old-phone-hmac");
        assertEquals("PHONE_SNAPSHOT_MISMATCH", queue.eligibilityRejectionReason(challenge, user, Instant.now()));

        when(user.getTelefone()).thenReturn("not-a-phone");
        assertEquals("PHONE_FORMAT_INVALID", queue.eligibilityRejectionReason(challenge, user, Instant.now()));

        when(user.getTelefone()).thenReturn("11987654321");
        challenge.setEmailSnapshotHash("email-hmac");
        challenge.setPhoneSnapshotHash("phone-hmac");
        assertNull(queue.eligibilityRejectionReason(challenge, user, Instant.now()));
    }

    private AdminAuthDeliveryQueueService createQueue() {
        return new AdminAuthDeliveryQueueService(deliveries, challenges, crypto, audit, authorization);
    }

    private AdminAuthChallenge challenge(UUID id, AdminAuthChallenge.State state) {
        AdminAuthChallenge challenge = new AdminAuthChallenge();
        challenge.setId(id);
        challenge.setState(state);
        challenge.setExpiresAt(Instant.now().plusSeconds(300));
        return challenge;
    }

    private AdminAuthDelivery delivery(UUID challengeId, AdminAuthChallenge challenge) {
        AdminAuthDelivery delivery = new AdminAuthDelivery();
        delivery.setId(UUID.randomUUID());
        delivery.setChallenge(challenge);
        delivery.setState(AdminAuthDelivery.State.PENDING);
        delivery.setAttemptCount(0);
        delivery.setUpdatedAt(Instant.now());
        return delivery;
    }
}
