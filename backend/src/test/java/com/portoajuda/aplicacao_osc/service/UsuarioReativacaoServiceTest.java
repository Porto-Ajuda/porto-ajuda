package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioReativacaoServiceTest {
    @Mock UsuarioRepository usuarioRepository;
    @Mock AutenticacaoAbusoUsuarioService autenticacaoAbuso;
    @Mock SessaoUsuarioService sessaoUsuarioService;
    @InjectMocks UsuarioReativacaoService service;

    @Test
    void accountCanReactivateDuringOriginalSixMonthPeriodAndMustLogInAgain() {
        Usuario account = mock(Usuario.class);
        LocalDateTime requestedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);
        when(usuarioRepository.findByEmailForUpdate(new Email("account@example.invalid")))
                .thenReturn(Optional.of(account));
        when(autenticacaoAbuso.verificarSenhaMesmoParaContaInexistente(account, "synthetic-password"))
                .thenReturn(true);
        when(account.isEnabled()).thenReturn(false);
        when(account.isEmailVerificado()).thenReturn(true);
        when(account.getDataSolicitacaoExclusao()).thenReturn(requestedAt);
        when(account.getDataExclusaoAgendada()).thenReturn(UsuarioService.calcularDataExclusao(requestedAt));
        when(account.getId()).thenReturn(93);

        assertTrue(service.cancelar(new Email("account@example.invalid"), "synthetic-password"));
        verify(account).setAtivo(true);
        verify(account).setDataSolicitacaoExclusao(null);
        verify(account).setDataExclusaoAgendada(null);
        verify(sessaoUsuarioService).revogarSessoesDoUsuario(93);
    }

    @Test
    void expiredOrInvalidDeletionStateIsNotReactivated() {
        Usuario account = mock(Usuario.class);
        LocalDateTime requestedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusMonths(7);
        when(usuarioRepository.findByEmailForUpdate(new Email("account@example.invalid")))
                .thenReturn(Optional.of(account));
        when(autenticacaoAbuso.verificarSenhaMesmoParaContaInexistente(account, "synthetic-password"))
                .thenReturn(true);
        when(account.isEnabled()).thenReturn(false);
        when(account.isEmailVerificado()).thenReturn(true);
        when(account.getDataSolicitacaoExclusao()).thenReturn(requestedAt);
        when(account.getDataExclusaoAgendada()).thenReturn(UsuarioService.calcularDataExclusao(requestedAt));

        assertFalse(service.cancelar(new Email("account@example.invalid"), "synthetic-password"));
        verify(account, never()).setAtivo(true);
        verify(sessaoUsuarioService, never()).revogarSessoesDoUsuario(93);
    }
}
