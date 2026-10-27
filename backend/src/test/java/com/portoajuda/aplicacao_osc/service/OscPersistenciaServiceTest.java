package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.dto.response.ResponseOscDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OscPersistenciaServiceTest {

    @Mock private OscRepository oscRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RoleRepository roleRepository;

    private OscPersistenciaService service;
    private Usuario usuario;
    private Role role;

    @BeforeEach
    void configurar() {
        service = new OscPersistenciaService(oscRepository, usuarioRepository, roleRepository);
        usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 7);
        ReflectionTestUtils.setField(usuario, "ativo", true);
        ReflectionTestUtils.setField(usuario, "emailVerificado", true);
        role = new Role();
        role.setNome("OSC");

    }

    @Test
    void salvaCausasOdsEnderecoEUsuarioResponsavelSemPix() {
        RequestOscDTO dados = dados(Set.of(3, 4, 17));
        Coordenadas coordenadas = new Coordenadas(-23.5, -46.6);
        when(oscRepository.existsByCnpj(any())).thenReturn(false);
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(usuario));
        when(roleRepository.findByNome("OSC")).thenReturn(Optional.of(role));
        when(oscRepository.save(any(Osc.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        ResponseOscDTO resposta = service.salvar(dados, 7, coordenadas, null);

        ArgumentCaptor<Osc> captor = ArgumentCaptor.forClass(Osc.class);
        verify(oscRepository).save(captor.capture());
        Osc salva = captor.getValue();

        assertEquals(Set.of(CausaSocial.EDUCACAO, CausaSocial.SAUDE), salva.getCausas());
        assertEquals(Set.of(3, 4, 17), salva.getOds());
        assertSame(usuario, salva.getUsuario());
        assertEquals("São Paulo", salva.getEstado());
        assertEquals(-23.5, salva.getLatitude());
        assertEquals("Apoio à educação", salva.getDescricaoCurta());
        assertTrue(usuario.getRoles().contains(role));
        assertEquals("Instituto Exemplo", resposta.nome());
        assertEquals(Set.of(3, 4, 17), resposta.ods());
    }

    @Test
    void rejeitaOdsForaDoIntervaloOficialAntesDeSalvar() {
        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> service.salvar(dados(Set.of(0, 18)), 7,
                        new Coordenadas(-23.5, -46.6), null));

        assertEquals(422, erro.getStatusCode().value());
        verify(oscRepository, never()).save(any());
    }

    @Test
    void rejeitaContaInativaOuNaoVerificadaAntesDeSalvar() {
        when(oscRepository.existsByCnpj(any())).thenReturn(false);
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(usuario));
        ReflectionTestUtils.setField(usuario, "emailVerificado", false);

        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> service.salvar(dados(Set.of()), 7,
                        new Coordenadas(-23.5, -46.6), null));

        assertEquals(403, erro.getStatusCode().value());
        verify(oscRepository, never()).save(any());
    }

    @Test
    void rejeitaCoordenadasAusentesOuInvalidas() {
        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> service.salvar(dados(Set.of()), 7,
                        new Coordenadas(0, Double.NaN), null));

        assertEquals(422, erro.getStatusCode().value());
        verify(oscRepository, never()).save(any());
    }

    private RequestOscDTO dados(Set<Integer> ods) {
        return new RequestOscDTO(
                "11222333000181",
                Set.of(CausaSocial.EDUCACAO, CausaSocial.SAUDE),
                ods,
                "Instituto Exemplo",
                "Apoio à educação",
                "Descrição institucional completa",
                2010,
                "contato@exemplo.org.br",
                null, null, null, null, null, null,
                "cidade", "Brasil", "São Paulo", "São Paulo", "01000-000",
                "Centro", "Rua Exemplo", "10", null,
                Set.of("segunda"), null, null,
                false, true, null, true, null, null, null, null, null);
    }
}
