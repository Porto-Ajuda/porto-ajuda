package com.portoajuda.aplicacao_osc.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthDeliveryQueueService {

    private static final java.time.Duration DELIVERY_LEASE = java.time.Duration.ofMinutes(2);
    private static final List<State> ACTIVE_DELIVERY_STATES = List.of(State.PENDING, State.PROCESSING);

    private final AdminAuthDeliveryRepository deliveryRepository;
    private final AdminAuthChallengeRepository challengeRepository;
    private final AdminAuthCodeSecurity crypto;
    private final AdminAuthAuditService audit;

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
        if (!eligible(challenge, usuario, now)) {
            if (challenge != null) {
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
        deliveryRepository.save(delivery);

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

    private boolean eligible(AdminAuthChallenge challenge, Usuario usuario, Instant now) {
        if (challenge == null || usuario == null
                || challenge.getState() != AdminAuthChallenge.State.ISSUING
                || challenge.getExpiresAt() == null || !challenge.getExpiresAt().isAfter(now)
                || !usuario.isEnabled() || !usuario.isEmailVerificado() || !usuario.isTelefoneVerificado()
                || usuario.getEmail() == null || usuario.getTelefone() == null
                || usuario.getTelefone().isBlank()) {
            return false;
        }
        boolean isAdmin = usuario.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!isAdmin || !crypto.accountKey(usuario.getEmail().valor()).equals(challenge.getEmailSnapshotHash())) {
            return false;
        }
        try {
            return crypto.phoneKey(SmsService.normalizarTelefoneBrasileiro(usuario.getTelefone()))
                    .equals(challenge.getPhoneSnapshotHash());
        } catch (SmsDeliveryException invalidPhone) {
            return false;
        }
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
