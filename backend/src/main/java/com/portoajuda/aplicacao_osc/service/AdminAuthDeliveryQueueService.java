package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery.Channel;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery.State;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.AdminAuthDeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthDeliveryQueueService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAuthDeliveryQueueService.class);
    private static final java.time.Duration DELIVERY_LEASE = java.time.Duration.ofMinutes(2);
    private static final List<State> ACTIVE_DELIVERY_STATES = List.of(State.PENDING, State.PROCESSING);

    private final AdminAuthDeliveryRepository deliveryRepository;
    private final AdminAuthChallengeRepository challengeRepository;
    private final AdminAuthCodeSecurity crypto;
    private final AdminAuthAuditService audit;
    private final AdminAuthorizationService adminAuthorization;

    @Transactional
    public ClaimedDelivery claimNext() {
        Instant now = Instant.now();
        Optional<AdminAuthDelivery> optional = deliveryRepository.claimNextPending();
        if (optional.isEmpty()) {
            return null;
        }

        AdminAuthDelivery delivery = optional.get();
        delivery.setState(State.PROCESSING);
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setLeaseUntil(now.plus(DELIVERY_LEASE));
        delivery.setUpdatedAt(now);
        deliveryRepository.saveAndFlush(delivery);

        AdminAuthChallenge challenge = challengeRepository.findById(delivery.getChallenge().getId())
                .orElse(null);
        Usuario usuario = challenge == null ? null : challenge.getUsuario();
        String rejectionReason = eligibilityRejectionReason(challenge, usuario, now);
        if (rejectionReason != null) {
            LOGGER.warn("Admin authentication delivery skipped before provider call; reason={}",
                    rejectionReason);
            if (challenge != null) {
                audit.registrar("ADMIN_AUTH_DELIVERY_INELIGIBLE_" + rejectionReason,
                        usuarioId(challenge), challenge.getId(), null, null);
                invalidate(challenge, "ADMIN_AUTH_DELIVERY_NO_LONGER_ELIGIBLE", now);
            } else {
                failDelivery(delivery, now);
            }
            return null;
        }

        String destination;
        try {
            destination = delivery.getChannel() == Channel.EMAIL
                    ? usuario.getEmail().valor()
                    : SmsService.normalizarTelefoneBrasileiro(usuario.getTelefone());
            String code = crypto.decryptDeliveryCode(
                    challenge.getId(), delivery.getChannel().name(), delivery.getEncryptedCode());
            return new ClaimedDelivery(delivery.getId(), challenge.getId(),
                    delivery.getChannel(), destination, code);
        } catch (RuntimeException invalidDeliveryData) {
            invalidate(challenge, "ADMIN_AUTH_DELIVERY_DATA_INVALID", now);
            return null;
        }
    }

    @Transactional
    public void finish(UUID deliveryId, boolean delivered) {
        Instant now = Instant.now();
        AdminAuthDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null || delivery.getState() != State.PROCESSING) {
            return;
        }
        AdminAuthChallenge challenge = challengeRepository.findById(delivery.getChallenge().getId())
                .orElse(null);
        if (!delivered || challenge == null
                || challenge.getState() != AdminAuthChallenge.State.ISSUING
                || challenge.getExpiresAt() == null
                || !challenge.getExpiresAt().isAfter(now)) {
            if (challenge != null && challenge.getState() == AdminAuthChallenge.State.ISSUING) {
                invalidate(challenge, "ADMIN_AUTH_DELIVERY_FAILURE", now);
            } else {
                failDelivery(delivery, now);
            }
            return;
        }

        delivery.setState(State.SENT);
        delivery.setEncryptedCode(null);
        delivery.setLeaseUntil(null);
        delivery.setUpdatedAt(now);
        // Flush before counting SENT rows; the next delivery may finish in this same transaction,
        // and the challenge must become PENDING as soon as both provider calls have succeeded.
        deliveryRepository.saveAndFlush(delivery);

        if (deliveryRepository.countByChallenge_IdAndState(challenge.getId(), State.SENT) == 2) {
            challenge.setState(AdminAuthChallenge.State.PENDING);
            challengeRepository.save(challenge);
            audit.registrar("ADMIN_AUTH_CHALLENGE_ISSUED", usuarioId(challenge), challenge.getId(), null, null);
        }
    }

    @Transactional
    public void expireStaleDeliveries() {
        Instant now = Instant.now();
        List<UUID> expired = deliveryRepository.findExpiredIssuingChallengeIds(now);
        deliveryRepository.expireDeliveries(now);
        for (UUID challengeId : expired) {
            challengeRepository.findById(challengeId).ifPresent(challenge ->
                    invalidate(challenge, "ADMIN_AUTH_DELIVERY_EXPIRED", now));
        }

        List<UUID> exhausted = deliveryRepository.findExhaustedIssuingChallengeIds(now);
        deliveryRepository.failExhaustedLeases(now);
        for (UUID challengeId : exhausted) {
            challengeRepository.findById(challengeId).ifPresent(challenge ->
                    invalidate(challenge, "ADMIN_AUTH_DELIVERY_RETRIES_EXHAUSTED", now));
        }
    }

    @Transactional
    public int clearInvalidatedForUser(Integer userId) {
        return deliveryRepository.clearInvalidatedForUser(userId, ACTIVE_DELIVERY_STATES, State.FAILED,
                AdminAuthChallenge.State.INVALIDATED, Instant.now());
    }

    @Transactional
    public void invalidateDeliveries(UUID challengeId, Instant now) {
        deliveryRepository.failChallengeDeliveries(
                challengeId, ACTIVE_DELIVERY_STATES, State.FAILED, now);
    }

    String eligibilityRejectionReason(
            AdminAuthChallenge challenge,
            Usuario usuario,
            Instant now) {
        if (challenge == null) return "CHALLENGE_MISSING";
        if (usuario == null) return "USER_MISSING";
        if (challenge.getState() != AdminAuthChallenge.State.ISSUING) return "CHALLENGE_STATE_NOT_ISSUING";
        if (challenge.getExpiresAt() == null || !challenge.getExpiresAt().isAfter(now)) {
            return "CHALLENGE_EXPIRED";
        }

        AdminAuthorizationService.PrivilegedSessionEligibility eligibility =
                adminAuthorization.privilegedSessionEligibility(usuario);
        if (eligibility != AdminAuthorizationService.PrivilegedSessionEligibility.ELIGIBLE) {
            return switch (eligibility) {
                case USER_MISSING -> "USER_MISSING";
                case USER_ID_MISSING -> "USER_ID_MISSING";
                case ACCOUNT_INACTIVE -> "ACCOUNT_INACTIVE";
                case EMAIL_UNVERIFIED -> "EMAIL_UNVERIFIED";
                case PHONE_UNVERIFIED -> "PHONE_UNVERIFIED";
                case EMAIL_MISSING -> "EMAIL_MISSING";
                case PHONE_MISSING -> "PHONE_MISSING";
                case ROLE_ADMIN_MISSING -> "ROLE_ADMIN_MISSING";
                case EMAIL_BLANK -> "EMAIL_BLANK";
                case PHONE_FORMAT_INVALID -> "PHONE_FORMAT_INVALID";
                case PHONE_NUMBER_INVALID -> "PHONE_NUMBER_INVALID";
                case ACTIVE_ASSIGNMENT_MISSING -> "ACTIVE_ASSIGNMENT_MISSING";
                case ASSIGNMENT_LOOKUP_UNAVAILABLE -> "ASSIGNMENT_LOOKUP_UNAVAILABLE";
                case ELIGIBLE -> null;
            };
        }
        if (!crypto.accountKey(usuario.getEmail().valor()).equals(challenge.getEmailSnapshotHash())) {
            return "EMAIL_SNAPSHOT_MISMATCH";
        }
        try {
            if (!crypto.phoneKey(SmsService.normalizarTelefoneBrasileiro(usuario.getTelefone()))
                    .equals(challenge.getPhoneSnapshotHash())) {
                return "PHONE_SNAPSHOT_MISMATCH";
            }
        } catch (SmsDeliveryException invalidPhone) {
            return "PHONE_FORMAT_INVALID";
        }
        return null;
    }

    private void invalidate(AdminAuthChallenge challenge, String event, Instant now) {
        challenge.setState(AdminAuthChallenge.State.FAILED);
        challenge.setInvalidatedAt(now);
        challenge.setDeliveryFailureAt(now);
        challenge.setEmailCodeHash(null);
        challenge.setSmsCodeHash(null);
        challengeRepository.save(challenge);
        deliveryRepository.failChallengeDeliveries(
                challenge.getId(), ACTIVE_DELIVERY_STATES, State.FAILED, now);
        audit.registrar(event, usuarioId(challenge), challenge.getId(), null, null);
    }

    private void failDelivery(AdminAuthDelivery delivery, Instant now) {
        delivery.setState(State.FAILED);
        delivery.setEncryptedCode(null);
        delivery.setLeaseUntil(null);
        delivery.setUpdatedAt(now);
        deliveryRepository.save(delivery);
    }

    private Integer usuarioId(AdminAuthChallenge challenge) {
        return challenge.getUsuario() == null ? null : challenge.getUsuario().getId();
    }

    /** No toString: delivery codes must never leak through accidental logging. */
    public static final class ClaimedDelivery {
        private final UUID deliveryId;
        private final UUID challengeId;
        private final Channel channel;
        private final String destination;
        private final String code;

        ClaimedDelivery(UUID deliveryId, UUID challengeId, Channel channel, String destination, String code) {
            this.deliveryId = deliveryId;
            this.challengeId = challengeId;
            this.channel = channel;
            this.destination = destination;
            this.code = code;
        }

        public UUID deliveryId() { return deliveryId; }
        public UUID challengeId() { return challengeId; }
        public Channel channel() { return channel; }
        public String destination() { return destination; }
        public String code() { return code; }
    }
}
