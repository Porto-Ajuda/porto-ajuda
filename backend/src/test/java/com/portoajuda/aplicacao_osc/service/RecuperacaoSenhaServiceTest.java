package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.portoajuda.aplicacao_osc.entity.RecuperacaoSenha;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.RecuperacaoSenhaRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {
    @Mock RecuperacaoSenhaRepository recuperacaoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock UsuarioService usuarioService;
    @Mock ApplicationEventPublisher eventos;
    RecuperacaoSenhaService service;

    @BeforeEach
    void setup() {
        String testKey = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("synthetic-test-key-min-32-bytes!!".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        service = new RecuperacaoSenhaService(recuperacaoRepository, usuarioRepository,
                usuarioService, eventos, testKey);
    }

    @Test
    void pepperAusenteOuCurtoFalhaFechado() {
        assertThrows(IllegalStateException.class, () -> new RecuperacaoSenhaService(
                recuperacaoRepository, usuarioRepository, usuarioService, eventos, ""));
        String weak = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("short".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> new RecuperacaoSenhaService(
                recuperacaoRepository, usuarioRepository, usuarioService, eventos, weak));
    }

    @Test
    void pedidoElegivelPersisteSomenteHashEPublicaEventoDepoisDaTransacao() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 17);
        usuario.setEmail(new Email("teste@example.org"));
        usuario.setEmailVerificado(true);
        when(usuarioRepository.findByEmailForUpdate(new Email("teste@example.org"))).thenReturn(Optional.of(usuario));
        when(recuperacaoRepository.countRecent(any(), any(), any())).thenReturn(0L);

        service.solicitar(" TESTE@example.org ", "127.0.0.1");

        ArgumentCaptor<RecuperacaoSenha> captor = ArgumentCaptor.forClass(RecuperacaoSenha.class);
        verify(recuperacaoRepository).save(captor.capture());
        assertNotNull(captor.getValue().getTokenHash());
        assertEquals(64, captor.getValue().getTokenHash().length());
        ArgumentCaptor<PasswordRecoveryRequested> evento = ArgumentCaptor.forClass(PasswordRecoveryRequested.class);
        verify(eventos).publishEvent(evento.capture());
        assertNotEquals(evento.getValue().token(), captor.getValue().getTokenHash());
        assertFalse(evento.getValue().toString().contains(evento.getValue().token()));
    }

    @Test
    void emailDesconhecidoPersisteLimiteSemCriarCredencialOuPublicarEvento() {
        when(usuarioRepository.findByEmailForUpdate(any())).thenReturn(Optional.empty());
        when(recuperacaoRepository.countRecent(any(), any(), any())).thenReturn(0L);

        service.solicitar("ausente@example.org", "127.0.0.1");

        ArgumentCaptor<RecuperacaoSenha> captor = ArgumentCaptor.forClass(RecuperacaoSenha.class);
        verify(recuperacaoRepository).save(captor.capture());
        assertNull(captor.getValue().getUsuario());
        assertNull(captor.getValue().getTokenHash());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void tokenExpiradoNaoAlteraSenha() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 17);
        RecuperacaoSenha credencial = new RecuperacaoSenha();
        credencial.setTokenHash("c".repeat(64));
        credencial.setExpiraEm(Instant.now().minusSeconds(1));
        credencial.setUsuario(usuario);
        when(recuperacaoRepository.countRecent(any(), any(), any())).thenReturn(0L);
        when(recuperacaoRepository.findByTokenHash(any())).thenReturn(Optional.of(credencial));
        when(recuperacaoRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(credencial));
        when(usuarioRepository.findByIdForUpdate(17)).thenReturn(Optional.of(usuario));

        assertFalse(service.redefinir("token-invalido", "SenhaNova1!", "SenhaNova1!", "127.0.0.1"));
        verify(usuarioService, never()).redefinirSenha(any(), any());
    }

    @Test
    void tokenJaConsumidoNaoPodeSerReutilizado() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 21);
        RecuperacaoSenha credencial = new RecuperacaoSenha();
        credencial.setUsuario(usuario);
        credencial.setConsumidoEm(Instant.now().minusSeconds(10));
        credencial.setExpiraEm(Instant.now().plusSeconds(600));
        when(recuperacaoRepository.countRecent(any(), any(), any())).thenReturn(0L);
        when(recuperacaoRepository.findByTokenHash(any())).thenReturn(Optional.of(credencial));
        when(recuperacaoRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(credencial));
        when(usuarioRepository.findByIdForUpdate(21)).thenReturn(Optional.of(usuario));

        assertFalse(service.redefinir("token-ja-usado", "SenhaNova1!", "SenhaNova1!", "127.0.0.1"));
        verify(usuarioRepository).findByIdForUpdate(21);
        verify(usuarioService, never()).validarNovaSenha(any(), any());
        verify(usuarioService, never()).redefinirSenha(any(), any());
    }

    @Test
    void redefinicaoValidaConsomeTokenRevogaSessoesPelaCamadaDeUsuarioEInvalidaCredenciaisAntigas() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 22);
        RecuperacaoSenha credencial = new RecuperacaoSenha();
        credencial.setUsuario(usuario);
        credencial.setExpiraEm(Instant.now().plusSeconds(600));
        when(recuperacaoRepository.countRecent(any(), any(), any())).thenReturn(0L);
        when(recuperacaoRepository.findByTokenHash(any())).thenReturn(Optional.of(credencial));
        when(recuperacaoRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(credencial));
        when(usuarioRepository.findByIdForUpdate(22)).thenReturn(Optional.of(usuario));

        assertTrue(service.redefinir("token-seguro", "SenhaNova1!", "SenhaNova1!", "127.0.0.1"));

        verify(usuarioService).validarNovaSenha(usuario, "SenhaNova1!");
        verify(usuarioService).redefinirSenha(usuario, "SenhaNova1!");
        assertNotNull(credencial.getConsumidoEm());
    }

    @Test
    void solicitacoesAcimaDoLimiteNaoCriamOuEnviamNovasCredenciais() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 19);
        usuario.setEmail(new Email("teste@example.org"));
        usuario.setEmailVerificado(true);
        when(usuarioRepository.findByEmailForUpdate(any())).thenReturn(Optional.of(usuario));
        when(recuperacaoRepository.countRecent(any(), any(), any())).thenReturn(3L);

        service.solicitar("teste@example.org", "127.0.0.1");

        verify(recuperacaoRepository, never()).invalidateForUser(any(), any());
        verify(eventos, never()).publishEvent(any());
        verify(recuperacaoRepository).save(argThat(item -> item.getTokenHash() == null));
    }
}
