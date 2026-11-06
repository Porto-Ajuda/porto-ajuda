package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import com.portoajuda.aplicacao_osc.entity.AdminPrivilegedSession;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.AdminPrivilegedSessionRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminPrivilegedSessionServiceTest {

    @Mock private AdminPrivilegedSessionRepository sessionRepository;
    @Mock private AdminAuthChallengeRepository challengeRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AdminAuthAuditService audit;

    private AdminPrivilegedSessionService service;
    private AdminAuthCodeSecurity crypto;
    private Usuario admin;
    private AdminAuthChallenge verifiedChallenge;

    @BeforeEach
    void setup() {
        crypto = new AdminAuthCodeSecurity("admin-session-test-pepper-at-least-thirty-two-bytes");
        service = new AdminPrivilegedSessionService(
                sessionRepository, challengeRepository, usuarioRepository, crypto, audit);
        admin = new Usuario();
        ReflectionTestUtils.setField(admin, "id", 42);
        admin.setEmail(new Email("admin@example.invalid"));
        Role role = new Role();
        role.setNome("ADMIN");
        admin.setRoles(new HashSet<>(Set.of(role)));
        verifiedChallenge = new AdminAuthChallenge();
        verifiedChallenge.setId(UUID.randomUUID());
        verifiedChallenge.setUsuario(admin);
        verifiedChallenge.setState(AdminAuthChallenge.State.VERIFIED);
        verifiedChallenge.setEmailVerifiedAt(Instant.now());
        verifiedChallenge.setSmsVerifiedAt(Instant.now());
        verifiedChallenge.setCompletedAt(Instant.now());
    }

    @Test
    void emiteCredencialAleatoriaEGravaSomenteSeuHashComLimitesCorretos() {
        when(challengeRepository.findByIdForUpdate(verifiedChallenge.getId()))
                .thenReturn(Optional.of(verifiedChallenge));
        when(usuarioRepository.findById(42)).thenReturn(Optional.of(admin));

        AdminPrivilegedSessionService.IssuedCredential issued =
                service.emitir(verifiedChallenge.getId(), "192.0.2.42");

        assertNotEquals(issued.rawCredential(), service.hashCredential(issued.rawCredential()));
        assertEquals(43, issued.rawCredential().length());
        assertTrue(Duration.between(Instant.now(), issued.expiresAt()).toMinutes() <= 120);
        ArgumentCaptor<AdminPrivilegedSession> captor = ArgumentCaptor.forClass(AdminPrivilegedSession.class);
        verify(sessionRepository).saveAndFlush(captor.capture());
        AdminPrivilegedSession stored = captor.getValue();
        assertEquals(service.hashCredential(issued.rawCredential()), stored.getCredentialHash());
        assertNotEquals(issued.rawCredential(), stored.getCredentialHash());
        assertTrue(Duration.between(stored.getCreatedAt(), stored.getAbsoluteExpiresAt()).toSeconds()
                <= AdminPrivilegedSessionService.ABSOLUTE_LIFETIME.toSeconds());
        assertTrue(Duration.between(stored.getLastActivityAt(), stored.getIdleExpiresAt()).toSeconds()
                <= AdminPrivilegedSessionService.INACTIVITY_LIFETIME.toSeconds());
        assertNull(stored.getRevokedAt());
    }

    @Test
    void naoEmiteSessaoSemMfaCompletaOuRoleAdminAtual() {
        verifiedChallenge.setState(AdminAuthChallenge.State.PENDING);
        when(challengeRepository.findByIdForUpdate(verifiedChallenge.getId()))
                .thenReturn(Optional.of(verifiedChallenge));
        assertThrows(IllegalStateException.class,
                () -> service.emitir(verifiedChallenge.getId(), "192.0.2.42"));
        verify(sessionRepository, never()).saveAndFlush(any());

        verifiedChallenge.setState(AdminAuthChallenge.State.VERIFIED);
        when(challengeRepository.findByIdForUpdate(verifiedChallenge.getId()))
                .thenReturn(Optional.of(verifiedChallenge));
        Usuario regular = new Usuario();
        ReflectionTestUtils.setField(regular, "id", 42);
        regular.setEmail(new Email("admin@example.invalid"));
        when(usuarioRepository.findById(42)).thenReturn(Optional.of(regular));
        assertThrows(IllegalStateException.class,
                () -> service.emitir(verifiedChallenge.getId(), "192.0.2.42"));
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void cadaRequisicaoRevalidaRoleContaEExpiracoesEAtualizaInatividade() {
        String raw = "A".repeat(43);
        String hash = service.hashCredential(raw);
        AdminPrivilegedSession session = session(raw, hash, admin);
        when(sessionRepository.findByCredentialHashForUpdate(hash)).thenReturn(Optional.of(session));
        when(usuarioRepository.findById(42)).thenReturn(Optional.of(admin));

        Optional<AdminPrivilegedSessionService.AuthenticatedAdmin> authenticated =
                service.autenticar(raw, "192.0.2.43");

        assertTrue(authenticated.isPresent());
        assertEquals(42, authenticated.get().usuario().getId());
        assertTrue(session.getLastActivityAt().isAfter(Instant.now().minusSeconds(2)));
        assertTrue(session.getIdleExpiresAt().isBefore(session.getAbsoluteExpiresAt()));
        verify(sessionRepository).save(session);
    }

    @Test
    void expiracaoAbsolutaOuInatividadeRevogaESuspendeAcesso() {
        String raw = "B".repeat(43);
        String hash = service.hashCredential(raw);
        AdminPrivilegedSession expired = session(raw, hash, admin);
        expired.setAbsoluteExpiresAt(Instant.now().minusSeconds(1));
        when(sessionRepository.findByCredentialHashForUpdate(hash)).thenReturn(Optional.of(expired));

        assertTrue(service.autenticar(raw, "192.0.2.44").isEmpty());
        assertEquals("ABSOLUTE_EXPIRED", expired.getRevocationReason());
        verify(sessionRepository).save(expired);
    }

    @Test
    void remocaoRoleAdminOuDesativacaoRevogaNaProximaRequisicao() {
        String raw = "C".repeat(43);
        String hash = service.hashCredential(raw);
        AdminPrivilegedSession session = session(raw, hash, admin);
        when(sessionRepository.findByCredentialHashForUpdate(hash)).thenReturn(Optional.of(session));
        Usuario inactive = new Usuario();
        ReflectionTestUtils.setField(inactive, "id", 42);
        inactive.setEmail(new Email("admin@example.invalid"));
        Role regularRole = new Role();
        regularRole.setNome("USUARIO");
        inactive.setRoles(new HashSet<>(Set.of(regularRole)));
        inactive.setAtivo(false);
        when(usuarioRepository.findById(42)).thenReturn(Optional.of(inactive));

        assertTrue(service.autenticar(raw, "192.0.2.45").isEmpty());
        assertEquals("ROLE_OR_ACCOUNT_INVALID", session.getRevocationReason());
        verify(sessionRepository).save(session);
    }

    @Test
    void logoutRevogaERevogacaoMassivaPorSenhaInvalidaOutrasSessoes() {
        String raw = "D".repeat(43);
        String hash = service.hashCredential(raw);
        AdminPrivilegedSession session = session(raw, hash, admin);
        when(sessionRepository.findByCredentialHashForUpdate(hash)).thenReturn(Optional.of(session));

        assertTrue(service.revogarPorCredencial(raw, "LOGOUT", "192.0.2.46"));
        assertEquals("LOGOUT", session.getRevocationReason());
        service.revogarTodasDoUsuario(42, "PASSWORD_CHANGED");
        verify(sessionRepository).revokeAllForUser(eq(42), any(Instant.class), eq("PASSWORD_CHANGED"));
    }

    private AdminPrivilegedSession session(String raw, String hash, Usuario user) {
        Instant now = Instant.now();
        AdminPrivilegedSession session = new AdminPrivilegedSession();
        session.setId(UUID.randomUUID());
        session.setUsuario(user);
        session.setChallenge(verifiedChallenge);
        session.setCredentialHash(hash);
        session.setCreatedAt(now.minusSeconds(10));
        session.setLastActivityAt(now.minusSeconds(2));
        session.setAbsoluteExpiresAt(now.plus(Duration.ofHours(2)));
        session.setIdleExpiresAt(now.plus(Duration.ofMinutes(18)));
        return session;
    }
}
