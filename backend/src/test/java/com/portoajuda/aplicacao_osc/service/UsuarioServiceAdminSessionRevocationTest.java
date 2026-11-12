package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentService;
import com.portoajuda.aplicacao_osc.admin.AdminRbacInvariantService;
import com.portoajuda.aplicacao_osc.dto.request.RequestAtualizarPerfilDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestLoginDTO;
import com.portoajuda.aplicacao_osc.repository.AdminAuthChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.CodigoAutenticacaoRepository;
import com.portoajuda.aplicacao_osc.repository.PhoneVerificationChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.RecuperacaoSenhaRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceAdminSessionRevocationTest {

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JwtService jwtService;
    @Mock private RoleRepository roleRepository;
    @Mock private EmailService emailService;
    @Mock private SessaoUsuarioService regularSessions;
    @Mock private SupabaseStorageService storageService;
    @Mock private AdminAuthChallengeRepository challengeRepository;
    @Mock private AdminAuthDeliveryQueueService deliveryQueue;
    @Mock private AdminPrivilegedSessionService adminSessions;
    @Mock private PhoneVerificationChallengeRepository phoneChallenges;
    @Mock private CodigoAutenticacaoRepository loginCodes;
    @Mock private RecuperacaoSenhaRepository recoveryCredentials;
    @Mock private AdminAssignmentService adminAssignmentService;
    @Mock private AdminRbacInvariantService adminRbacInvariantService;

    @InjectMocks private UsuarioService service;

    @Test
    void alteracaoDeSenhaRevogaSessoesComunsDesafiosESessoesAdministrativas() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 73);
        when(usuarioRepository.findByIdForUpdate(73)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaAtual1!", null)).thenReturn(true);
        when(passwordEncoder.matches("SenhaNova1!", null)).thenReturn(false);
        when(passwordEncoder.encode("SenhaNova1!")).thenReturn("encoded-new-password");

        service.changePassword("SenhaAtual1!", "SenhaNova1!", "SenhaNova1!", 73, 810L, true);

        ArgumentCaptor<List<com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge.State>> states =
                ArgumentCaptor.forClass(List.class);
        verify(challengeRepository).invalidatePendingForUser(eq(73), any(Instant.class), any(), states.capture());
        assertTrue(states.getValue().contains(com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge.State.VERIFIED));
        verify(deliveryQueue).clearInvalidatedForUser(73);
        verify(phoneChallenges).invalidatePendingForUser(eq(73), any(Instant.class));
        verify(loginCodes).deleteByUsuarioId(73);
        verify(recoveryCredentials).invalidateForUser(eq(73), any(Instant.class));
        verify(adminSessions).revogarTodasDoUsuario(73, "PASSWORD_CHANGED");
        verify(regularSessions).preservarSessaoAtualDuranteTrocaSenha(73, 810L, true);
    }

    @Test
    void trocaComDispositivosMantidosPreservaSessoesComunsEMantemInvalidacaoPrivilegiada() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 73);
        when(usuarioRepository.findByIdForUpdate(73)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaAtual1!", null)).thenReturn(true);
        when(passwordEncoder.matches("SenhaNova1!", null)).thenReturn(false);
        when(passwordEncoder.encode("SenhaNova1!")).thenReturn("encoded-new-password");

        service.changePassword("SenhaAtual1!", "SenhaNova1!", "SenhaNova1!", 73, 810L, false);

        verify(regularSessions).preservarSessaoAtualDuranteTrocaSenha(73, 810L, false);
        verify(regularSessions, never()).revogarSessoesDoUsuario(any());
        verify(loginCodes).deleteByUsuarioId(73);
        verify(recoveryCredentials).invalidateForUser(eq(73), any(Instant.class));
        verify(adminSessions).revogarTodasDoUsuario(73, "PASSWORD_CHANGED");
    }

    @Test
    void trocaAutenticadaRejeitaAusenciaDaSessaoVerificadaAntesDeAlterarCredenciais() {
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> service.changePassword("SenhaAtual1!", "SenhaNova1!", "SenhaNova1!", 73, null, true));
        verify(usuarioRepository, never()).findByIdForUpdate(any());
        verify(passwordEncoder, never()).encode(any());
        verify(regularSessions, never()).revogarSessoesDoUsuario(any());
        verify(adminSessions, never()).revogarTodasDoUsuario(any(), any());
    }

    @Test
    void senhaAtualIncorretaNaoAlteraCredenciaisNemRevogaSessoes() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 73);
        when(usuarioRepository.findByIdForUpdate(73)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-errada", null)).thenReturn(false);

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> service.changePassword("senha-errada", "SenhaNova1!", "SenhaNova1!", 73, 810L, true));

        verify(passwordEncoder, never()).encode(any());
        verify(regularSessions, never()).revogarSessoesDoUsuario(any());
        verify(regularSessions, never()).preservarSessaoAtualDuranteTrocaSenha(any(), any(), anyBoolean());
        verify(adminSessions, never()).revogarTodasDoUsuario(any(), any());
    }

    @Test
    void rejeitaConfirmacaoDiferenteEReusoDaSenhaAtual() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 73);
        when(usuarioRepository.findByIdForUpdate(73)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaAtual1!", null)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("SenhaAtual1!", "SenhaNova1!", "SenhaOutra1!", 73, 810L, true));
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("SenhaAtual1!", "SenhaAtual1!", "SenhaAtual1!", 73, 810L, true));
        verify(regularSessions, never()).revogarSessoesDoUsuario(any());
        verify(regularSessions, never()).preservarSessaoAtualDuranteTrocaSenha(any(), any(), anyBoolean());
    }

    @Test
    void redefinicaoTambemRevogaSessaoOtpEChavesDeRecuperacao() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 73);
        when(usuarioRepository.findByIdForUpdate(73)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaNova1!", null)).thenReturn(false);
        when(passwordEncoder.encode("SenhaNova1!")).thenReturn("encoded-new-password");

        service.redefinirSenha(usuario, "SenhaNova1!");

        verify(regularSessions).revogarSessoesDoUsuario(73);
        verify(loginCodes).deleteByUsuarioId(73);
        verify(recoveryCredentials).invalidateForUser(eq(73), any(Instant.class));
        verify(adminSessions).revogarTodasDoUsuario(73, "PASSWORD_CHANGED");
    }

    @Test
    void loginBloqueiaContaAntesDeValidarSenhaEEmitirSessao() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 74);
        usuario.setCpf(new Cpf("52998224725"));
        usuario.setEmail(new Email("login@example.invalid"));
        usuario.setNome("Conta Sintética");
        usuario.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setSenha("password-hash");
        usuario.setEmailVerificado(true);
        when(usuarioRepository.findByEmailForUpdate(new Email("login@example.invalid")))
                .thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Password1!", "password-hash")).thenReturn(true);
        SessaoUsuario sessao = new SessaoUsuario(usuario, "f".repeat(64), Instant.now(),
                Instant.now().plusSeconds(3600), false);
        ReflectionTestUtils.setField(sessao, "id", 810L);
        when(regularSessions.criarSessao(usuario, false))
                .thenReturn(new SessaoUsuarioService.SessaoCriada(sessao, "synthetic-refresh-token"));
        when(jwtService.generateToken(usuario, 810L)).thenReturn("synthetic-access-token");

        var result = service.login(new RequestLoginDTO("login@example.invalid", "Password1!", false));

        assertEquals("synthetic-access-token", result.accessToken());
        InOrder order = inOrder(usuarioRepository, passwordEncoder, regularSessions);
        order.verify(usuarioRepository).findByEmailForUpdate(new Email("login@example.invalid"));
        order.verify(passwordEncoder).matches("Password1!", "password-hash");
        order.verify(regularSessions).criarSessao(usuario, false);
    }

    @Test
    void alteracaoDoTelefoneRemoveVerificacaoEInvalidaDesafiosPendentes() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 73);
        usuario.setTelefone("5511912345678");
        usuario.setTelefoneVerificado(true);
        when(usuarioRepository.findById(73)).thenReturn(Optional.of(usuario));

        service.atualizarPerfil(73,
                new RequestAtualizarPerfilDTO(null, "5511987654321", null));

        assertFalse(usuario.isTelefoneVerificado());
        verify(phoneChallenges).invalidatePendingForUser(eq(73), any(Instant.class));
        verify(challengeRepository).invalidatePendingForUser(
                eq(73), any(Instant.class), any(), anyList());
        verify(deliveryQueue).clearInvalidatedForUser(73);
        verify(adminSessions).revogarTodasDoUsuario(73, "CONTACT_CHANGED");
    }
}
