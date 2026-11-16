package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentService;
import com.portoajuda.aplicacao_osc.admin.AdminRbacInvariantService;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.utils.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceExclusaoTest {

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JwtService jwtService;
    @Mock private RoleRepository roleRepository;
    @Mock private EmailService emailService;
    @Mock private SessaoUsuarioService sessaoUsuarioService;
    @Mock private AdminPrivilegedSessionService adminPrivilegedSessionService;
    @Mock private SupabaseStorageService supabaseStorageService;
    @Mock private AdminAssignmentService adminAssignmentService;
    @Mock private AdminRbacInvariantService adminRbacInvariantService;
    @Mock private AutenticacaoAbusoUsuarioService autenticacaoAbuso;
    @Mock private UsuarioReativacaoService usuarioReativacaoService;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void prazoUsaSeisMesesDeCalendarioNoFimDoMes() {
        LocalDateTime solicitadaEm = LocalDateTime.of(2024, 8, 31, 14, 25);

        assertEquals(
                LocalDateTime.of(2025, 2, 28, 14, 25),
                UsuarioService.calcularDataExclusao(solicitadaEm));
    }

    @Test
    void prazoPreservaDiaBissextoAoSomarSeisMesesDeCalendario() {
        LocalDateTime solicitadaEm = LocalDateTime.of(2024, 2, 29, 14, 25);

        assertEquals(
                LocalDateTime.of(2024, 8, 29, 14, 25),
                UsuarioService.calcularDataExclusao(solicitadaEm));
    }

    @Test
    void primeiraSolicitacaoDesativaContaGravaPrazoERevogaSessoes() {
        Usuario usuario = mock(Usuario.class);
        when(usuarioRepository.findByIdForUpdate(42)).thenReturn(Optional.of(usuario));
        when(usuario.isEnabled()).thenReturn(true);
        when(usuario.isEmailVerificado()).thenReturn(true);

        LocalDateTime prazo = usuarioService.solicitarExclusao(42);

        var solicitacaoCaptor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(usuario).setDataSolicitacaoExclusao(solicitacaoCaptor.capture());
        assertEquals(
                solicitacaoCaptor.getValue().plusMonths(6),
                prazo);
        verify(usuario).setDataExclusaoAgendada(prazo);
        verify(usuario).setAtivo(false);
        verify(sessaoUsuarioService).revogarSessoesDoUsuario(42);
        verify(adminPrivilegedSessionService).revogarTodasDoUsuario(42, "ADMIN_REVOKED");
    }

    @Test
    void solicitacaoRepetidaRetornaPrazoOriginalSemProlongar() {
        Usuario usuario = mock(Usuario.class);
        LocalDateTime solicitadaEm = LocalDateTime.of(2026, 1, 31, 9, 0);
        LocalDateTime prazoOriginal = solicitadaEm.plusMonths(6);

        when(usuarioRepository.findByIdForUpdate(42)).thenReturn(Optional.of(usuario));
        when(usuario.getDataSolicitacaoExclusao()).thenReturn(solicitadaEm);
        when(usuario.getDataExclusaoAgendada()).thenReturn(prazoOriginal);
        when(usuario.isEnabled()).thenReturn(false);

        assertEquals(prazoOriginal, usuarioService.solicitarExclusao(42));
        verify(usuario, never()).setDataSolicitacaoExclusao(any());
        verify(usuario, never()).setDataExclusaoAgendada(any());
        verifyNoInteractions(sessaoUsuarioService);
    }

    @Test
    void solicitacaoRepetidaComDatasInconsistentesNaoOcultaEstadoInvalido() {
        Usuario usuario = mock(Usuario.class);
        LocalDateTime solicitadaEm = LocalDateTime.of(2026, 1, 31, 9, 0);

        when(usuarioRepository.findByIdForUpdate(42)).thenReturn(Optional.of(usuario));
        when(usuario.getDataSolicitacaoExclusao()).thenReturn(solicitadaEm);
        when(usuario.getDataExclusaoAgendada()).thenReturn(solicitadaEm.plusDays(180));
        when(usuario.isEnabled()).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> usuarioService.solicitarExclusao(42));
        verifyNoInteractions(sessaoUsuarioService);
    }

    @Test
    void contaInativaSemSolicitacaoNaoGanhaPrazoNovo() {
        Usuario usuario = mock(Usuario.class);
        when(usuarioRepository.findByIdForUpdate(42)).thenReturn(Optional.of(usuario));
        when(usuario.isEnabled()).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> usuarioService.solicitarExclusao(42));
        verifyNoInteractions(sessaoUsuarioService);
    }

    @Test
    void cancelamentoDentroDoPrazoPassaPelosLimitesAntesDoNucleoTransacional() {
        when(autenticacaoAbuso.registrarTentativaLoginPorIp("198.51.100.42")).thenReturn(true);
        when(usuarioRepository.existsByEmail(new Email("conta@example.test"))).thenReturn(true);
        when(autenticacaoAbuso.permitirReativacaoConta("conta@example.test")).thenReturn(true);
        when(usuarioReativacaoService.cancelar(new Email("conta@example.test"), "senha-teste"))
                .thenReturn(true);

        assertTrue(usuarioService.cancelarExclusao("conta@example.test", "senha-teste", "198.51.100.42"));
        verify(usuarioReativacaoService).cancelar(new Email("conta@example.test"), "senha-teste");
    }

    @Test
    void limiteDeReativacaoImpedeAcessoAoNucleoSemMudarCicloDeVida() {
        when(autenticacaoAbuso.registrarTentativaLoginPorIp("198.51.100.42")).thenReturn(true);
        when(usuarioRepository.existsByEmail(new Email("conta@example.test"))).thenReturn(true);
        when(autenticacaoAbuso.permitirReativacaoConta("conta@example.test")).thenReturn(false);

        assertFalse(usuarioService.cancelarExclusao("conta@example.test", "senha-teste", "198.51.100.42"));
        verifyNoInteractions(usuarioReativacaoService);
    }
}
