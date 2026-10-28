package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestNewMembroDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OscServiceMembroTest {

    @Mock private OscRepository oscRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OscMembrosRepository membrosRepository;
    @Mock private GeocodingService geocodingService;
    @Mock private ValidacaoCnpjOscService validacaoCnpjOscService;
    @Mock private OscPersistenciaService persistenciaService;
    @Mock private SupabaseStorageService supabaseStorageService;
    @Mock private Usuario proprietario;
    @Mock private Usuario novoMembro;
    @Mock private Osc osc;
    @Mock private Role roleOsc;

    @InjectMocks private OscService service;

    @Test
    void adicionaUsuarioQueAindaNaoPertenceAOutraOsc() throws Exception {
        prepararOscDoProprietario();
        when(usuarioRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(novoMembro));
        when(novoMembro.getId()).thenReturn(2);
        when(usuarioRepository.usuarioIsMember(2)).thenReturn(false);
        when(roleRepository.findByNome("OSC")).thenReturn(Optional.of(roleOsc));
        Set<Role> roles = new HashSet<>();
        when(novoMembro.getRoles()).thenReturn(roles);

        service.addMembro(proprietario,
                new RequestNewMembroDTO("membro@exemplo.org", "Coordenador"));

        ArgumentCaptor<OscMembros> membroSalvo = ArgumentCaptor.forClass(OscMembros.class);
        verify(membrosRepository).save(membroSalvo.capture());
        assertSame(osc, membroSalvo.getValue().getOsc());
        assertSame(novoMembro, membroSalvo.getValue().getUsuario());
        assertEquals("Coordenador", membroSalvo.getValue().getCargoOsc());
        assertTrue(membroSalvo.getValue().getDataCriacao().isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(roles.contains(roleOsc));
    }

    @Test
    void rejeitaUsuarioQueJaPertenceAOutraOsc() throws Exception {
        prepararOscDoProprietario();
        when(usuarioRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(novoMembro));
        when(novoMembro.getId()).thenReturn(2);
        when(usuarioRepository.usuarioIsMember(2)).thenReturn(true);

        assertThrows(AccessDeniedException.class,
                () -> service.addMembro(proprietario,
                        new RequestNewMembroDTO("membro@exemplo.org", "Coordenador")));

        verify(membrosRepository, never()).save(any());
        verify(roleRepository, never()).findByNome("OSC");
    }

    private void prepararOscDoProprietario() {
        when(proprietario.getId()).thenReturn(1);
        when(oscRepository.findByUsuarioId(1)).thenReturn(Optional.of(osc));
        when(osc.getId()).thenReturn(8);
        when(oscRepository.existsByIdAndUsuarioId(8, 1)).thenReturn(true);
    }
}
