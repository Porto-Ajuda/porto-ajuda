package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.AdminPrivilegedSession;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.AdminPrivilegedSessionRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPrivilegedSessionService {

    public static final Duration ABSOLUTE_LIFETIME = Duration.ofHours(2);
    public static final Duration INACTIVITY_LIFETIME = Duration.ofMinutes(20);
    private static final int TOKEN_BYTES = 32;
    private static final List<String> REASONS = List.of(
            "LOGOUT", "PASSWORD_CHANGED", "ROLE_OR_ACCOUNT_INVALID", "ABSOLUTE_EXPIRED",
            "IDLE_EXPIRED", "ADMIN_REVOKED", "CONTACT_CHANGED");

    private final AdminPrivilegedSessionRepository sessionRepository;
    private final AdminAuthChallengeRepository challengeRepository;
    private final UsuarioRepository usuarioRepository;
    private final AdminAuthCodeSecurity codeSecurity;
    private final AdminAuthAuditService audit;
    private final AdminAuthorizationService adminAuthorization;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminPrivilegedSessionService(
            AdminPrivilegedSessionRepository sessionRepository,
            AdminAuthChallengeRepository challengeRepository,
            UsuarioRepository usuarioRepository,
            AdminAuthCodeSecurity codeSecurity,
            AdminAuthAuditService audit,
            AdminAuthorizationService adminAuthorization) {
        this.sessionRepository = sessionRepository;
        this.challengeRepository = challengeRepository;
        this.usuarioRepository = usuarioRepository;
        this.codeSecurity = codeSecurity;
        this.audit = audit;
        this.adminAuthorization = adminAuthorization;
    }

    /** Must be called in the same transaction that changes the MFA challenge to VERIFIED. */
    @Transactional
    public IssuedCredential emitir(UUID challengeId, String remoteAddress) {
        AdminAuthChallenge challenge = challengeRepository.findByIdForUpdate(challengeId).orElse(null);
        if (challenge == null || challenge.getState() != AdminAuthChallenge.State.VERIFIED
                || challenge.getEmailVerifiedAt() == null || challenge.getSmsVerifiedAt() == null
                || challenge.getCompletedAt() == null || challenge.getUsuario() == null) {
            throw new IllegalStateException("Desafio MFA administrativo não pode emitir sessão.");
        }

        Usuario usuario = usuarioRepository.findById(challenge.getUsuario().getId()).orElse(null);
        if (!elegivel(usuario)) {
            throw new IllegalStateException("Conta não elegível para sessão administrativa.");
        }

        Instant now = Instant.now();
        Instant absoluteExpiry = now.plus(ABSOLUTE_LIFETIME);
        String rawCredential = gerarCredencial();

        AdminPrivilegedSession session = new AdminPrivilegedSession();
        session.setId(UUID.randomUUID());
        session.setUsuario(usuario);
        session.setChallenge(challenge);
        session.setCredentialHash(hashCredential(rawCredential));
        session.setCreatedAt(now);
        session.setLastActivityAt(now);
        session.setAbsoluteExpiresAt(absoluteExpiry);
        session.setIdleExpiresAt(min(now.plus(INACTIVITY_LIFETIME), absoluteExpiry));
        session.setCreatedIpHash(codeSecurity.ipKey(remoteAddress));
        sessionRepository.saveAndFlush(session);
        audit.registrar("ADMIN_SESSION_ISSUED", usuario.getId(), challengeId,
                usuario.getEmail().valor(), remoteAddress);
        return new IssuedCredential(rawCredential, absoluteExpiry);
    }

    /** Locks and revalidates the server-side record on every request under /admin. */
    @Transactional
    public Optional<AuthenticatedAdmin> autenticar(String rawCredential, String remoteAddress) {
        if (rawCredential == null || rawCredential.isBlank()) {
            return Optional.empty();
        }
        AdminPrivilegedSession session = sessionRepository
                .findByCredentialHashForUpdate(hashCredential(rawCredential))
                .orElse(null);
        if (session == null || session.getRevokedAt() != null) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        if (session.getAbsoluteExpiresAt() == null || !session.getAbsoluteExpiresAt().isAfter(now)) {
            revogar(session, "ABSOLUTE_EXPIRED", now, remoteAddress);
            return Optional.empty();
        }
        if (session.getIdleExpiresAt() == null || !session.getIdleExpiresAt().isAfter(now)) {
            revogar(session, "IDLE_EXPIRED", now, remoteAddress);
            return Optional.empty();
        }

        Usuario usuario = usuarioRepository.findById(session.getUsuario().getId()).orElse(null);
        if (!elegivel(usuario)) {
            revogar(session, "ROLE_OR_ACCOUNT_INVALID", now, remoteAddress);
            return Optional.empty();
        }

        session.setLastActivityAt(now);
        session.setIdleExpiresAt(min(now.plus(INACTIVITY_LIFETIME), session.getAbsoluteExpiresAt()));
        sessionRepository.save(session);
        return Optional.of(new AuthenticatedAdmin(
                usuario,
                session.getId(),
                session.getAbsoluteExpiresAt(),
                session.getIdleExpiresAt()));
    }

    @Transactional
    public boolean revogarPorCredencial(String rawCredential, String reason, String remoteAddress) {
        if (rawCredential == null || rawCredential.isBlank()) {
            return false;
        }
        AdminPrivilegedSession session = sessionRepository
                .findByCredentialHashForUpdate(hashCredential(rawCredential))
                .orElse(null);
        if (session == null || session.getRevokedAt() != null) {
            return false;
        }
        revogar(session, validarMotivo(reason), Instant.now(), remoteAddress);
        return true;
    }

    @Transactional
    public int revogarTodasDoUsuario(Integer userId, String reason) {
        if (userId == null) {
            return 0;
        }
        int revoked = sessionRepository.revokeAllForUser(userId, Instant.now(), validarMotivo(reason));
        if (revoked > 0) {
            usuarioRepository.findById(userId).ifPresent(user -> audit.registrar(
                    "ADMIN_SESSION_REVOKED_" + validarMotivo(reason), userId, null,
                    user.getEmail() == null ? null : user.getEmail().valor(), null));
        }
        return revoked;
    }

    public String hashCredential(String rawCredential) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawCredential.getBytes(StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Não foi possível validar a sessão administrativa.");
        }
    }

    private String gerarCredencial() {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private boolean elegivel(Usuario usuario) {
        return adminAuthorization.isEligibleForPrivilegedSession(usuario);
    }

    private void revogar(AdminPrivilegedSession session, String reason, Instant now, String remoteAddress) {
        session.setRevokedAt(now);
        session.setRevocationReason(reason);
        sessionRepository.save(session);
        Usuario usuario = session.getUsuario();
        audit.registrar("ADMIN_SESSION_REVOKED_" + reason, usuario.getId(),
                session.getChallenge().getId(), usuario.getEmail().valor(), remoteAddress);
    }

    private String validarMotivo(String reason) {
        return REASONS.contains(reason) ? reason : "ADMIN_REVOKED";
    }

    private Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    /** Intentionally not a record and has no credential-bearing toString(). */
    public static final class IssuedCredential {
        private final String rawCredential;
        private final Instant expiresAt;

        IssuedCredential(String rawCredential, Instant expiresAt) {
            this.rawCredential = rawCredential;
            this.expiresAt = expiresAt;
        }

        public String rawCredential() { return rawCredential; }
        public Instant expiresAt() { return expiresAt; }
    }

    public record AuthenticatedAdmin(
            Usuario usuario,
            UUID sessionId,
            Instant absoluteExpiresAt,
            Instant idleExpiresAt) {
    }
}
