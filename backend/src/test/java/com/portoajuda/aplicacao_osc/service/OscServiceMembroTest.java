package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestNewMembroDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.OscMembroId;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
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

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OscServiceMembroTest {

    @Mock
    private OscRepository oscRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OscMembrosRepository membrosRepository;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private ValidacaoCnpjOscService validacaoCnpjOscService;

    @Mock
    private OscPersistenciaService persistenciaService;

    @Mock
    private SupabaseStorageService supabaseStorageService;

    @Mock
    private LimpezaService limpezaService;

    @Mock
    private Usuario proprietario;

    @Mock
    private Usuario novoMembro;

    @Mock
    private Osc osc;

    @Mock
    private Role roleOsc;

    @InjectMocks
    private OscService service;

    @Test
    void permiteAdicionarUsuarioMesmoQueParticipeDeOutraOsc() {

        prepararOscDoProprietario();

        when(usuarioRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(novoMembro));

        when(novoMembro.getId()).thenReturn(2);

        when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 2))
                .thenReturn(Optional.empty());

        when(roleRepository.findByNome("OSC"))
                .thenReturn(Optional.of(roleOsc));

        Set<Role> roles = new HashSet<>();

        when(novoMembro.getRoles()).thenReturn(roles);

        RequestNewMembroDTO dto = new RequestNewMembroDTO(
                "membro@exemplo.org",
                CargoOsc.EDITOR);

        service.addMembro(8, proprietario, dto);

        ArgumentCaptor<OscMembros> membroSalvo = ArgumentCaptor.forClass(OscMembros.class);

        verify(membrosRepository).save(membroSalvo.capture());

        OscMembros membro = membroSalvo.getValue();

        assertSame(osc, membro.getOsc());
        assertSame(novoMembro, membro.getUsuario());

        assertEquals(
                new OscMembroId(8, 2),
                membro.getId());

        assertEquals(
                CargoOsc.EDITOR,
                membro.getCargoOsc());

        assertTrue(
                membro.getDataCriacao()
                        .isBefore(LocalDateTime.now().plusSeconds(1)));

        assertTrue(roles.contains(roleOsc));
    }

    @Test
    void rejeitaUsuarioQueJaPertenceAEstaOsc() {

        prepararOscDoProprietario();

        when(usuarioRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(novoMembro));

        when(novoMembro.getId()).thenReturn(2);

        OscMembros vinculoExistente = new OscMembros();
        when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 2))
                .thenReturn(Optional.of(vinculoExistente));

        RequestNewMembroDTO dto = new RequestNewMembroDTO(
                "membro@exemplo.org",
                CargoOsc.EDITOR);

        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> service.addMembro(8, proprietario, dto));

        assertEquals(409, erro.getStatusCode().value());

        verify(membrosRepository, never()).save(any());

        verify(roleRepository, never()).findByNome("OSC");
    }

    @Test
    void usuarioInexistenteRetorna404EmVezDeErroInterno() {
        prepararOscDoProprietario();
        when(usuarioRepository.findByEmail(any(Email.class))).thenReturn(Optional.empty());

        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.addMembro(8, proprietario,
                        new RequestNewMembroDTO("inexistente@exemplo.org", CargoOsc.MEMBRO)));

        assertEquals(404, erro.getStatusCode().value());
        verify(membrosRepository, never()).save(any());
    }

    @Test
    void membroPodeConsultarMembrosDaOsc() {
        Usuario solicitante = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(solicitante, "id", 3);
        when(proprietario.getId()).thenReturn(1);
        when(osc.getId()).thenReturn(8);
        when(osc.getUsuario()).thenReturn(proprietario);
        OscMembros vinculoMembro = new OscMembros();
        vinculoMembro.setOsc(osc);
        vinculoMembro.setUsuario(solicitante);
        vinculoMembro.setCargoOsc(CargoOsc.MEMBRO);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 3)).thenReturn(Optional.of(vinculoMembro));
        when(oscRepository.findById(8)).thenReturn(Optional.of(osc));
        when(membrosRepository.findAllByOsc_IdOrderByUsuario_NomeAsc(8)).thenReturn(List.of());

        assertTrue(service.listarMembros(8, solicitante).isEmpty());
    }

    @Test
    void impedeQueSolicitanteAdicioneASiMesmo() {
        prepararOscDoProprietario();
        when(usuarioRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(proprietario));
        when(proprietario.getId()).thenReturn(1);

        assertThrows(ResponseStatusException.class, () -> service.addMembro(
                8,
                proprietario,
                new RequestNewMembroDTO("criador@exemplo.org", CargoOsc.EDITOR)));

        verify(membrosRepository, never()).save(any());
    }

    @Test
    void criadorPodeTransferirCargoEAtualizaTitularidadeOriginal() {
        prepararOscDoProprietario();
        OscMembros destino = new OscMembros();
        destino.setOsc(osc);
        destino.setUsuario(novoMembro);
        destino.setCargoOsc(CargoOsc.EDITOR);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 2))
                .thenReturn(Optional.of(destino));

        service.alterarCargoMembro(8, 2, proprietario, CargoOsc.CRIADOR);

        assertEquals(CargoOsc.ADMINISTRADOR,
                membrosRepository.findByOsc_IdAndUsuario_Id(8, 1).orElseThrow().getCargoOsc());
        assertEquals(CargoOsc.CRIADOR, destino.getCargoOsc());
        verify(osc).setUsuario(novoMembro);
        verify(oscRepository).save(osc);
    }

    @Test
    void administradorNaoPodeRebaixarOutroAdministrador() {
        when(oscRepository.findForUpdateById(8)).thenReturn(Optional.of(osc));
        when(osc.getId()).thenReturn(8);
        when(proprietario.getId()).thenReturn(1);
        when(osc.getUsuario()).thenReturn(proprietario);
        Usuario administrador = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(administrador, "id", 3);
        OscMembros vinculoAdmin = new OscMembros();
        vinculoAdmin.setOsc(osc);
        vinculoAdmin.setUsuario(administrador);
        vinculoAdmin.setCargoOsc(CargoOsc.ADMINISTRADOR);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 3))
                .thenReturn(Optional.of(vinculoAdmin));

        OscMembros destino = new OscMembros();
        destino.setOsc(osc);
        destino.setUsuario(novoMembro);
        destino.setCargoOsc(CargoOsc.ADMINISTRADOR);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 2))
                .thenReturn(Optional.of(destino));

        assertThrows(ResponseStatusException.class, () -> service.alterarCargoMembro(
                8, 2, administrador, CargoOsc.EDITOR));

        verify(membrosRepository, never()).save(destino);
    }

    @Test
    void editorNaoPodeAdicionarMembros() {
        prepararOscDoProprietario();
        Usuario editor = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(editor, "id", 3);
        OscMembros vinculoEditor = new OscMembros();
        vinculoEditor.setCargoOsc(CargoOsc.EDITOR);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 3))
                .thenReturn(Optional.of(vinculoEditor));

        assertThrows(ResponseStatusException.class, () -> service.addMembro(
                8, editor, new RequestNewMembroDTO("novo@exemplo.org", CargoOsc.MEMBRO)));

        verify(usuarioRepository, never()).findByEmail(any(Email.class));
    }

    @Test
    void administradorPodeAdicionarMembro() {
        prepararOscDoProprietario();
        Usuario administrador = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(administrador, "id", 3);
        OscMembros vinculoAdmin = new OscMembros();
        vinculoAdmin.setCargoOsc(CargoOsc.ADMINISTRADOR);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 3))
                .thenReturn(Optional.of(vinculoAdmin));
        when(usuarioRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(novoMembro));
        when(novoMembro.getId()).thenReturn(2);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 2))
                .thenReturn(Optional.empty());
        when(roleRepository.findByNome("OSC")).thenReturn(Optional.of(roleOsc));
        when(novoMembro.getRoles()).thenReturn(new HashSet<>());

        service.addMembro(8, administrador,
                new RequestNewMembroDTO("membro@exemplo.org", CargoOsc.MEMBRO));

        verify(membrosRepository).save(any(OscMembros.class));
    }

    @Test
    void editorNaoPodeExcluirOsc() {
        when(oscRepository.findForUpdateById(8)).thenReturn(Optional.of(osc));
        when(osc.getId()).thenReturn(8);
        Usuario proprietarioReal = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(proprietarioReal, "id", 1);
        when(osc.getUsuario()).thenReturn(proprietarioReal);
        Usuario editor = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(editor, "id", 3);
        OscMembros vinculoEditor = new OscMembros();
        vinculoEditor.setCargoOsc(CargoOsc.EDITOR);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 3))
                .thenReturn(Optional.of(vinculoEditor));

        assertThrows(ResponseStatusException.class, () -> service.delete(8, editor));

        verify(limpezaService, never()).excluirOscGerenciada(any());
    }

    @Test
    void criadorNaoPodeRemoverSeuProprioVinculo() {
        prepararOscDoProprietario();
        OscMembros vinculoCriador = membrosRepository
                .findByOsc_IdAndUsuario_Id(8, 1).orElseThrow();
        when(membrosRepository.findByOsc_IdAndUsuario_Email(8, new Email("criador@exemplo.org")))
                .thenReturn(Optional.of(vinculoCriador));

        assertThrows(ResponseStatusException.class, () -> service.removeMembro(
                8, proprietario, new Email("criador@exemplo.org")));

        verify(membrosRepository, never()).delete(vinculoCriador);
    }

    private void prepararOscDoProprietario() {

        when(proprietario.getId()).thenReturn(1);

        when(oscRepository.findForUpdateById(8))
                .thenReturn(Optional.of(osc));

        when(osc.getId()).thenReturn(8);

        when(osc.getUsuario()).thenReturn(proprietario);

        OscMembros vinculoCriador = new OscMembros();
        vinculoCriador.setOsc(osc);
        vinculoCriador.setUsuario(proprietario);
        vinculoCriador.setCargoOsc(CargoOsc.CRIADOR);
        lenient().when(membrosRepository.findByOsc_IdAndUsuario_Id(8, 1))
                .thenReturn(Optional.of(vinculoCriador));
    }
}
