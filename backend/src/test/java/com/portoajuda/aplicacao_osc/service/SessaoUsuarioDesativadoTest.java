package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.SessaoUsuarioRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessaoUsuarioDesativadoTest {

    @Test
    void refreshDeContaDesativadaRevogaSessaoESemEmitirCredenciais() {
        SessaoUsuarioRepository repository = mock(SessaoUsuarioRepository.class);
        SessaoUsuarioService service = new SessaoUsuarioService(repository);
        SessaoUsuario sessao = mock(SessaoUsuario.class);
        Usuario usuario = mock(Usuario.class);

        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(sessao));
        when(sessao.isRevogada()).thenReturn(false);
        when(sessao.getExpiraEm()).thenReturn(Instant.now().plusSeconds(3600));
        when(sessao.isLembrarDeMim()).thenReturn(false);
        when(sessao.getUsuario()).thenReturn(usuario);
        when(usuario.isEnabled()).thenReturn(false);

        assertTrue(service.renovarSessao("refresh-teste").isEmpty());

        verify(sessao).setRevogadaEm(org.mockito.ArgumentMatchers.any(Instant.class));
        verify(repository).save(sessao);
    }
}
