package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.dto.request.AdminAuthStartRequest;
import com.portoajuda.aplicacao_osc.dto.request.AdminAuthVerifyRequest;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery.Channel;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery.State;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.AdminAuthDeliveryRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fase 1: valida credenciais e dois fatores, mas não emite sessão administrativa. */
@Service
public class AdminAuthenticationService {

    public enum VerificationResult {
        REJECTED,
        PENDING,
        VERIFIED
    }

    private static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration LOCKOUT = Duration.ofHours(1);
    private static final Duration RATE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final int MAX_STARTS_PER_ACCOUNT = 3;
    private static final int MAX_STARTS_PER_IP = 20;

    private final UsuarioRepository usuarioRepository;
    private final AdminAuthChallengeRepository challengeRepository;
    private final AdminAuthDeliveryRepository deliveryRepository;
    private final AdminAuthCodeSecurity crypto;
    private final AdminAuthControlsService controls;
    private final AdminAuthAuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AdminPrivilegedSessionService adminSessionService;
    private final AdminAuthorizationService adminAuthorization;
    private final String dummyPasswordHash;

    public AdminAuthenticationService(
            UsuarioRepository usuarioRepository,
            AdminAuthChallengeRepository challengeRepository,
            AdminAuthDeliveryRepository deliveryRepository,
            AdminAuthCodeSecurity crypto,
            AdminAuthControlsService controls,
            AdminAuthAuditService audit,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AdminPrivilegedSessionService adminSessionService,
            AdminAuthorizationService adminAuthorization,
            @Value("${admin-auth.dummy-password-hash:}") String configuredDummyHash) {
        this.usuarioRepository = usuarioRepository;
        this.challengeRepository = challengeRepository;
        this.deliveryRepository = deliveryRepository;
        this.crypto = crypto;
        this.controls = controls;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.adminSessionService = adminSessionService;
        this.adminAuthorization = adminAuthorization;
        this.dummyPasswordHash = configuredDummyHash == null || configuredDummyHash.isBlank()
                ? passwordEncoder.encode(UUID.randomUUID().toString())
                : configuredDummyHash;
    }

    @Transactional
    public UUID iniciar(AdminAuthStartRequest request, String remoteAddress) {
        Instant now = Instant.now();
        String email = normalizarEmail(request.email());
        String accountHash = crypto.accountKey(email);
        String ipHash = crypto.ipKey(remoteAddress);
        UUID decoyId = UUID.randomUUID();

        if (controls.isLocked("ACCOUNT_FAILURE", accountHash, now)
                || controls.isLocked("START_ACCOUNT", accountHash, now)
                || controls.isLocked("START_IP", ipHash, now)) {
            audit.registrar("ADMIN_AUTH_START_THROTTLED", null, null, email, remoteAddress);
            return decoyId;
        }

        controls.increment("START_ACCOUNT", accountHash, now, RATE_WINDOW,
                MAX_STARTS_PER_ACCOUNT + 1, RATE_WINDOW, true);
        controls.increment("START_IP", ipHash, now, RATE_WINDOW,
                MAX_STARTS_PER_IP + 1, RATE_WINDOW, true);
        if (controls.isLocked("START_ACCOUNT", accountHash, now)
                || controls.isLocked("START_IP", ipHash, now)) {
            audit.registrar("ADMIN_AUTH_START_THROTTLED", null, null, email, remoteAddress);
            return decoyId;
        }

        // Serialize password validation and challenge creation with password changes.
        Usuario usuario = usuarioRepository.findByEmailForUpdate(new Email(email)).orElse(null);
        boolean passwordMatches = usuario == null
                ? passwordEncoder.matches(request.senha(), dummyPasswordHash)
                : passwordEncoder.matches(request.senha(), usuario.getSenha());

        if (usuario == null || !passwordMatches) {
            if (usuario != null && temRoleAdmin(usuario)) {
                registrarFalha(accountHash, ipHash, usuario.getId(), null, email, remoteAddress, now,
                        "ADMIN_AUTH_CREDENTIAL_FAILURE");
            } else {
                controls.increment("IP_FAILURE", ipHash, now, RATE_WINDOW,
                        MAX_STARTS_PER_IP + 1, RATE_WINDOW, true);
                audit.registrar("ADMIN_AUTH_CREDENTIAL_FAILURE", null, null, email, remoteAddress);
            }
            return decoyId;
        }

        if (!adminAuthorization.isEligibleForPrivilegedSession(usuario)) {
            audit.registrar("ADMIN_AUTH_INELIGIBLE", usuario.getId(), null, email, remoteAddress);
            return decoyId;
        }

        final String phone;
        try {
            phone = SmsService.normalizarTelefoneBrasileiro(usuario.getTelefone());
        } catch (SmsDeliveryException invalidContact) {
            audit.registrar("ADMIN_AUTH_INVALID_VERIFIED_PHONE", usuario.getId(), null, email, remoteAddress);
            return decoyId;
        }

        invalidarDesafiosAtivos(usuario.getId(), now);

        UUID challengeId = UUID.randomUUID();
        String emailCode = crypto.generateCode();
        String smsCode;
        do {
            smsCode = crypto.generateCode();
        } while (emailCode.equals(smsCode));

        AdminAuthChallenge challenge = new AdminAuthChallenge();
        challenge.setId(challengeId);
        challenge.setUsuario(usuario);
        challenge.setAccountKeyHash(accountHash);
        challenge.setIpKeyHash(ipHash);
        challenge.setEmailSnapshotHash(crypto.accountKey(usuario.getEmail().valor()));
        challenge.setPhoneSnapshotHash(crypto.phoneKey(phone));
        challenge.setEmailCodeHash(crypto.hashCode(challengeId, "EMAIL", emailCode));
        challenge.setSmsCodeHash(crypto.hashCode(challengeId, "SMS", smsCode));
        challenge.setEmailAttempts(0);
        challenge.setSmsAttempts(0);
        challenge.setFailedAttempts(0);
        challenge.setState(AdminAuthChallenge.State.ISSUING);
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plus(CHALLENGE_LIFETIME));
        challengeRepository.saveAndFlush(challenge);
        deliveryRepository.saveAll(List.of(
                criarEntrega(challenge, Channel.EMAIL, emailCode, now),
                criarEntrega(challenge, Channel.SMS, smsCode, now)));
        return challengeId;
    }

    @Transactional
    VerificationResult verificar(UUID challengeId, AdminAuthVerifyRequest request, String remoteAddress) {
        Instant now = Instant.now();
        String ipHash = crypto.ipKey(remoteAddress);
        if (controls.isLocked("VERIFY_IP", ipHash, now)) {
            return VerificationResult.REJECTED;
        }
        controls.increment("VERIFY_IP", ipHash, now, RATE_WINDOW,
                61, RATE_WINDOW, true);
        if (controls.isLocked("VERIFY_IP", ipHash, now)) {
            return VerificationResult.REJECTED;
        }
        AdminAuthChallenge challenge = challengeRepository.findByIdForUpdate(challengeId).orElse(null);
        if (challenge == null) {
            audit.registrar("ADMIN_AUTH_UNKNOWN_CHALLENGE", null, challengeId, null, remoteAddress);
            return VerificationResult.REJECTED;
        }
        if (!challenge.isPendingAt(now)) {
            if (challenge.getState() == AdminAuthChallenge.State.PENDING
                    && challenge.getExpiresAt() != null
                    && !challenge.getExpiresAt().isAfter(now)) {
                invalidar(challenge, "ADMIN_AUTH_CHALLENGE_EXPIRED");
            }
            return VerificationResult.REJECTED;
        }

        Usuario usuario = challenge.getUsuario() == null
                ? null
                : usuarioRepository.findById(challenge.getUsuario().getId()).orElse(null);
        if (!elegivel(usuario)
                || !crypto.accountKey(usuario.getEmail().valor()).equals(challenge.getEmailSnapshotHash())
                || !telefoneAindaCorresponde(usuario, challenge)) {
            invalidar(challenge, "ADMIN_AUTH_CHALLENGE_NO_LONGER_ELIGIBLE");
            if (challenge.getUsuario() != null) {
                invalidarDesafiosAtivos(challenge.getUsuario().getId(), now);
            }
            return VerificationResult.REJECTED;
        }

        boolean hadInput = false;
        boolean failed = false;
        if (challenge.getEmailVerifiedAt() == null && naoVazio(request.emailCode())) {
            hadInput = true;
            if (crypto.matchesCode(challengeId, "EMAIL", request.emailCode(), challenge.getEmailCodeHash())) {
                challenge.setEmailVerifiedAt(now);
                challenge.setEmailCodeHash(null);
            } else {
                challenge.setEmailAttempts(challenge.getEmailAttempts() + 1);
                failed = true;
            }
        } else if (challenge.getEmailVerifiedAt() != null && naoVazio(request.emailCode())) {
            hadInput = true;
            challenge.setEmailAttempts(challenge.getEmailAttempts() + 1);
            failed = true;
        }

        if (challenge.getSmsVerifiedAt() == null && naoVazio(request.smsCode())) {
            hadInput = true;
            if (crypto.matchesCode(challengeId, "SMS", request.smsCode(), challenge.getSmsCodeHash())) {
                challenge.setSmsVerifiedAt(now);
                challenge.setSmsCodeHash(null);
            } else {
                challenge.setSmsAttempts(challenge.getSmsAttempts() + 1);
                failed = true;
            }
        } else if (challenge.getSmsVerifiedAt() != null && naoVazio(request.smsCode())) {
            hadInput = true;
            challenge.setSmsAttempts(challenge.getSmsAttempts() + 1);
            failed = true;
        }

        if (!hadInput) {
            if (challenge.getEmailVerifiedAt() == null) {
                challenge.setEmailAttempts(challenge.getEmailAttempts() + 1);
            }
            if (challenge.getSmsVerifiedAt() == null) {
                challenge.setSmsAttempts(challenge.getSmsAttempts() + 1);
            }
            failed = true;
        }

        if (failed) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            boolean channelExhausted = challenge.getEmailAttempts() >= MAX_CODE_ATTEMPTS
                    || challenge.getSmsAttempts() >= MAX_CODE_ATTEMPTS;
            String accountHash = challenge.getAccountKeyHash();
            int consecutiveFailures = controls.increment("ACCOUNT_FAILURE", accountHash, now, null,
                    MAX_CONSECUTIVE_FAILURES, LOCKOUT, false);
            controls.increment("IP_FAILURE", challenge.getIpKeyHash(), now, RATE_WINDOW,
                    MAX_STARTS_PER_IP + 1, RATE_WINDOW, true);
            boolean accountLocked = controls.isLocked("ACCOUNT_FAILURE", accountHash, now);
            if (channelExhausted || accountLocked
                    || challenge.getFailedAttempts() >= MAX_CONSECUTIVE_FAILURES) {
                challenge.setState(AdminAuthChallenge.State.FAILED);
                challenge.setInvalidatedAt(now);
                challenge.setEmailCodeHash(null);
                challenge.setSmsCodeHash(null);
                invalidarDesafiosAtivos(usuario.getId(), now);
                audit.registrar(accountLocked ? "ADMIN_AUTH_ACCOUNT_LOCKED" : "ADMIN_AUTH_CHALLENGE_LOCKED",
                        usuario.getId(), challengeId, usuario.getEmail().valor(), remoteAddress);
                if (accountLocked && consecutiveFailures == MAX_CONSECUTIVE_FAILURES) {
                    notificarDemaisAdministradores(usuario.getId(), challengeId, now);
                }
            } else {
                audit.registrar("ADMIN_AUTH_CODE_FAILURE", usuario.getId(), challengeId,
                        usuario.getEmail().valor(), remoteAddress);
            }
            challengeRepository.save(challenge);
            return VerificationResult.REJECTED;
        }

        if (challenge.getEmailVerifiedAt() != null && challenge.getSmsVerifiedAt() != null) {
            challenge.setState(AdminAuthChallenge.State.VERIFIED);
            challenge.setCompletedAt(now);
            challengeRepository.save(challenge);
            controls.reset("ACCOUNT_FAILURE", challenge.getAccountKeyHash(), now);
            audit.registrar("ADMIN_AUTH_MFA_VERIFIED", usuario.getId(), challengeId,
                    usuario.getEmail().valor(), remoteAddress);
            return VerificationResult.VERIFIED;
        }

        challengeRepository.save(challenge);
        return VerificationResult.PENDING;
    }

    /** Completes both factors and creates the privileged session in the same DB transaction. */
    @Transactional
    public VerificationOutcome verificarEEmitirSessao(
            UUID challengeId, AdminAuthVerifyRequest request, String remoteAddress) {
        VerificationResult result = verificar(challengeId, request, remoteAddress);
        if (result != VerificationResult.VERIFIED) {
            return new VerificationOutcome(result, null);
        }
        AdminPrivilegedSessionService.IssuedCredential credential =
                adminSessionService.emitir(challengeId, remoteAddress);
        return new VerificationOutcome(result, credential);
    }

    /** No credential-bearing toString or JSON serialization. */
    public static final class VerificationOutcome {
        private final VerificationResult result;
        private final AdminPrivilegedSessionService.IssuedCredential credential;

        VerificationOutcome(
                VerificationResult result,
                AdminPrivilegedSessionService.IssuedCredential credential) {
            this.result = result;
            this.credential = credential;
        }

        public VerificationResult result() { return result; }
        public AdminPrivilegedSessionService.IssuedCredential credential() { return credential; }
    }

    private void registrarFalha(
            String accountHash,
            String ipHash,
            Integer userId,
            UUID challengeId,
            String email,
            String ip,
            Instant now,
            String event) {
        boolean wasLocked = controls.isLocked("ACCOUNT_FAILURE", accountHash, now);
        int consecutiveFailures = controls.increment("ACCOUNT_FAILURE", accountHash, now, null,
                MAX_CONSECUTIVE_FAILURES, LOCKOUT, false);
        controls.increment("IP_FAILURE", ipHash, now, RATE_WINDOW,
                MAX_STARTS_PER_IP + 1, RATE_WINDOW, true);
        boolean locked = controls.isLocked("ACCOUNT_FAILURE", accountHash, now);
        audit.registrar(locked ? "ADMIN_AUTH_ACCOUNT_LOCKED" : event,
                userId, challengeId, email, ip);
        if (locked && !wasLocked && consecutiveFailures == MAX_CONSECUTIVE_FAILURES) {
            notificarDemaisAdministradores(userId, challengeId, now);
        }
    }

    private void notificarDemaisAdministradores(Integer excludedUserId, UUID challengeId, Instant occurredAt) {
        String incidentId = challengeId == null ? UUID.randomUUID().toString() : challengeId.toString();
        for (Usuario destinatario : usuarioRepository.listarAdministradoresAtivosParaIncidente(excludedUserId)) {
            try {
                emailService.enviarAlertaIncidenteAutenticacaoAdmin(
                        destinatario.getEmail().valor(),
                        incidentId,
                        occurredAt,
                        "Cinco falhas consecutivas de autenticação administrativa; bloqueio de uma hora");
            } catch (RuntimeException falhaEntrega) {
                audit.registrar("ADMIN_AUTH_ALERT_DELIVERY_FAILURE", null, challengeId, null, null);
            }
        }
    }

    private void invalidar(AdminAuthChallenge challenge, String event) {
        Instant now = Instant.now();
        challenge.setState(AdminAuthChallenge.State.FAILED);
        challenge.setInvalidatedAt(now);
        challenge.setEmailCodeHash(null);
        challenge.setSmsCodeHash(null);
        challengeRepository.save(challenge);
        deliveryRepository.failChallengeDeliveries(
                challenge.getId(), List.of(State.PENDING, State.PROCESSING), State.FAILED, now);
        audit.registrar(event,
                challenge.getUsuario() == null ? null : challenge.getUsuario().getId(),
                challenge.getId(),
                challenge.getUsuario() == null ? null : challenge.getUsuario().getEmail().valor(),
                null);
    }

    private void invalidarDesafiosAtivos(Integer userId, Instant now) {
        challengeRepository.invalidatePendingForUser(
                userId, now, AdminAuthChallenge.State.INVALIDATED,
                List.of(AdminAuthChallenge.State.ISSUING, AdminAuthChallenge.State.PENDING));
        deliveryRepository.clearInvalidatedForUser(
                userId, List.of(State.PENDING, State.PROCESSING), State.FAILED,
                AdminAuthChallenge.State.INVALIDATED, now);
    }

    private AdminAuthDelivery criarEntrega(
            AdminAuthChallenge challenge, Channel channel, String code, Instant now) {
        AdminAuthDelivery delivery = new AdminAuthDelivery();
        delivery.setId(UUID.randomUUID());
        delivery.setChallenge(challenge);
        delivery.setChannel(channel);
        delivery.setEncryptedCode(crypto.encryptDeliveryCode(challenge.getId(), channel.name(), code));
        delivery.setState(State.PENDING);
        delivery.setAttemptCount(0);
        delivery.setCreatedAt(now);
        delivery.setUpdatedAt(now);
        return delivery;
    }

    private boolean elegivel(Usuario usuario) {
        return adminAuthorization.isEligibleForPrivilegedSession(usuario);
    }

    private boolean temRoleAdmin(Usuario usuario) {
        return usuario.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private boolean telefoneAindaCorresponde(Usuario usuario, AdminAuthChallenge challenge) {
        try {
            String current = SmsService.normalizarTelefoneBrasileiro(usuario.getTelefone());
            return crypto.phoneKey(current).equals(challenge.getPhoneSnapshotHash());
        } catch (SmsDeliveryException erro) {
            return false;
        }
    }

    private String normalizarEmail(String email) {
        return new Email(email).valor().trim().toLowerCase(Locale.ROOT);
    }

    private boolean naoVazio(String value) {
        return value != null && !value.isBlank();
    }
}
