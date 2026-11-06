package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.PhoneVerificationChallenge;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.PhoneVerificationChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final Duration CODE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RATE_WINDOW = Duration.ofHours(1);
    private static final int MAX_ISSUANCE_PER_ACCOUNT = 3;
    private static final int MAX_ISSUANCE_PER_IP = 10;
    private static final int MAX_ATTEMPTS = 5;

    private final UsuarioRepository usuarioRepository;
    private final PhoneVerificationChallengeRepository challengeRepository;
    private final AdminAuthCodeSecurity crypto;
    private final AdminAuthControlsService controls;
    private final AdminAuthAuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;

    @Transactional
    public UUID iniciar(Integer userId, String password, String remoteAddress) {
        UUID decoyId = UUID.randomUUID();
        Instant now = Instant.now();
        Usuario usuario = userId == null ? null : usuarioRepository.findById(userId).orElse(null);
        if (usuario == null || !usuario.isEnabled() || !usuario.isEmailVerificado()
                || usuario.getEmail() == null || password == null
                || !passwordEncoder.matches(password, usuario.getSenha())
                || usuario.getTelefone() == null || usuario.getTelefone().isBlank()) {
            audit.registrar("PHONE_VERIFICATION_INELIGIBLE", userId, decoyId,
                    usuario == null || usuario.getEmail() == null ? null : usuario.getEmail().valor(),
                    remoteAddress);
            return decoyId;
        }

        String accountHash = crypto.accountKey(usuario.getEmail().valor());
        String ipHash = crypto.ipKey(remoteAddress);
        if (controls.isLocked("PHONE_ISSUE_ACCOUNT", accountHash, now)
                || controls.isLocked("PHONE_ISSUE_IP", ipHash, now)) {
            audit.registrar("PHONE_VERIFICATION_RATE_LIMITED", userId, null,
                    usuario.getEmail().valor(), remoteAddress);
            return decoyId;
        }
        controls.increment("PHONE_ISSUE_ACCOUNT", accountHash, now, RATE_WINDOW,
                MAX_ISSUANCE_PER_ACCOUNT + 1, RATE_WINDOW, true);
        controls.increment("PHONE_ISSUE_IP", ipHash, now, RATE_WINDOW,
                MAX_ISSUANCE_PER_IP + 1, RATE_WINDOW, true);
        if (controls.isLocked("PHONE_ISSUE_ACCOUNT", accountHash, now)
                || controls.isLocked("PHONE_ISSUE_IP", ipHash, now)) {
            audit.registrar("PHONE_VERIFICATION_RATE_LIMITED", userId, null,
                    usuario.getEmail().valor(), remoteAddress);
            return decoyId;
        }

        final String phone;
        try {
            phone = SmsService.normalizarTelefoneBrasileiro(usuario.getTelefone());
        } catch (SmsDeliveryException invalidPhone) {
            audit.registrar("PHONE_VERIFICATION_INVALID_PHONE", userId, null,
                    usuario.getEmail().valor(), remoteAddress);
            return decoyId;
        }

        challengeRepository.invalidatePendingForUser(userId, now);
        UUID challengeId = UUID.randomUUID();
        String code = crypto.generateCode();
        PhoneVerificationChallenge challenge = new PhoneVerificationChallenge();
        challenge.setId(challengeId);
        challenge.setUsuario(usuario);
        challenge.setPhoneSnapshotHash(crypto.phoneKey(phone));
        challenge.setCodeHash(crypto.hashCode(challengeId, "PHONE", code));
        challenge.setAttempts(0);
        challenge.setState("ISSUING");
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plus(CODE_LIFETIME));
        challengeRepository.saveAndFlush(challenge);

        try {
            smsService.enviarSms(
                    phone,
                    "Porto Ajuda: seu código de confirmação de telefone é " + code
                            + ". Expira em 5 minutos. Não compartilhe.",
                    challengeId + ":phone-verification");
            challenge.setState("PENDING");
        } catch (RuntimeException deliveryFailure) {
            challenge.setState("FAILED");
            challenge.setInvalidatedAt(Instant.now());
            challenge.setCodeHash(null);
            audit.registrar("PHONE_VERIFICATION_DELIVERY_FAILURE", userId, challengeId,
                    usuario.getEmail().valor(), remoteAddress);
        }
        challengeRepository.save(challenge);
        return challengeId;
    }

    @Transactional
    public boolean confirmar(Integer userId, UUID challengeId, String candidate, String remoteAddress) {
        Instant now = Instant.now();
        String ipHash = crypto.ipKey(remoteAddress);
        if (controls.isLocked("PHONE_VERIFY_IP", ipHash, now)) {
            return false;
        }
        controls.increment("PHONE_VERIFY_IP", ipHash, now, RATE_WINDOW,
                31, RATE_WINDOW, true);
        if (controls.isLocked("PHONE_VERIFY_IP", ipHash, now)) {
            return false;
        }
        PhoneVerificationChallenge challenge = challengeRepository
                .findByIdForUpdate(challengeId)
                .orElse(null);
        if (challenge == null || challenge.getUsuario() == null
                || !challenge.getUsuario().getId().equals(userId)
                || !"PENDING".equals(challenge.getState())) {
            return false;
        }

        if (!challenge.getExpiresAt().isAfter(now)) {
            invalidar(challenge, now);
            return false;
        }

        Usuario usuario = usuarioRepository.findById(userId).orElse(null);
        if (usuario == null || !usuario.isEnabled() || !usuario.isEmailVerificado()
                || usuario.getTelefone() == null || usuario.getTelefone().isBlank()) {
            invalidar(challenge, now);
            return false;
        }

        String currentPhone;
        try {
            currentPhone = SmsService.normalizarTelefoneBrasileiro(usuario.getTelefone());
        } catch (SmsDeliveryException invalidPhone) {
            invalidar(challenge, now);
            return false;
        }
        if (!crypto.phoneKey(currentPhone).equals(challenge.getPhoneSnapshotHash())) {
            invalidar(challenge, now);
            return false;
        }

        if (crypto.matchesCode(challengeId, "PHONE", candidate, challenge.getCodeHash())) {
            challenge.setState("VERIFIED");
            challenge.setVerifiedAt(now);
            challenge.setCodeHash(null);
            usuario.setTelefoneVerificado(true);
            usuarioRepository.save(usuario);
            challengeRepository.save(challenge);
            audit.registrar("PHONE_VERIFICATION_SUCCEEDED", userId, challengeId,
                    usuario.getEmail().valor(), remoteAddress);
            return true;
        }

        challenge.setAttempts(challenge.getAttempts() + 1);
        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            invalidar(challenge, now);
            audit.registrar("PHONE_VERIFICATION_CHALLENGE_LOCKED", userId, challengeId,
                    usuario.getEmail().valor(), remoteAddress);
        } else {
            challengeRepository.save(challenge);
            audit.registrar("PHONE_VERIFICATION_CODE_FAILURE", userId, challengeId,
                    usuario.getEmail().valor(), remoteAddress);
        }
        return false;
    }

    private void invalidar(PhoneVerificationChallenge challenge, Instant now) {
        challenge.setState("INVALIDATED");
        challenge.setInvalidatedAt(now);
        challenge.setCodeHash(null);
        challengeRepository.save(challenge);
    }
}
