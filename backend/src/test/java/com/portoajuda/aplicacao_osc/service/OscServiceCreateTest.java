package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.dto.response.ConsultaCnpjDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseOscDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OscServiceCreateTest {

    private static final String CNPJ = "11222333000181";

    @Mock private OscRepository oscRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OscMembrosRepository membrosRepository;
    @Mock private GeocodingService geocodingService;
    @Mock private ValidacaoCnpjOscService validacaoCnpjOscService;
    @Mock private OscPersistenciaService persistenciaService;
    @Mock private SupabaseStorageService supabaseStorageService;

    private OscService service;
    private Usuario usuario;

    @BeforeEach
    void configurar() {
        service = new OscService(
                oscRepository,
                usuarioRepository,
                roleRepository,
                membrosRepository,
                geocodingService,
                validacaoCnpjOscService,
                persistenciaService,
                supabaseStorageService);
        usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 25);
    }

    @Test
    void validaCnpjGeocodificaEEncaminhaParaPersistencia() {
        RequestOscDTO dados = dados();
        Coordenadas coordenadas = new Coordenadas(-23.55, -46.63);
        ResponseOscDTO resposta = new ResponseOscDTO(
                81, CNPJ, "Instituto Exemplo", Set.of(CausaSocial.EDUCACAO), Set.of(4));
        when(oscRepository.existsByCnpj(any())).thenReturn(false);
        when(validacaoCnpjOscService.validar(CNPJ)).thenReturn(
                new ConsultaCnpjDTO(CNPJ, "Instituto Exemplo", null, "2010-01-01",
                        "Ativa", "Associação privada", true, null));
        when(geocodingService.buscarCoordenadas(
                "Brasil", "São Paulo", "01000-000", "São Paulo", "Rua Exemplo", "10"))
                .thenReturn(coordenadas);
        when(persistenciaService.salvar(dados, 25, coordenadas, null)).thenReturn(resposta);

        ResponseOscDTO resultado = service.create(dados, usuario, null);

        assertEquals(81, resultado.id());
        assertEquals(Set.of(4), resultado.ods());
        verify(persistenciaService).salvar(dados, 25, coordenadas, null);
    }

    @Test
    void cnpjInelegivelNaoGeocodificaNemPersiste() {
        RequestOscDTO dados = dados();
        when(oscRepository.existsByCnpj(any())).thenReturn(false);
        when(validacaoCnpjOscService.validar(CNPJ)).thenReturn(
                new ConsultaCnpjDTO(CNPJ, null, null, null, "Inapta", null,
                        false, "O CNPJ está inapto."));

        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> service.create(dados, usuario, null));

        assertEquals(422, erro.getStatusCode().value());
        verifyNoInteractions(geocodingService, persistenciaService);
    }

    @Test
    void enderecoNaoLocalizadoNaoPersisteOsc() {
        RequestOscDTO dados = dados();
        when(oscRepository.existsByCnpj(any())).thenReturn(false);
        when(validacaoCnpjOscService.validar(CNPJ)).thenReturn(
                new ConsultaCnpjDTO(CNPJ, "Instituto Exemplo", null, "2010-01-01",
                        "Ativa", "Associação privada", true, null));
        when(geocodingService.buscarCoordenadas(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "Endereço não localizado."));

        assertThrows(ResponseStatusException.class, () -> service.create(dados, usuario, null));
        verifyNoInteractions(persistenciaService);
    }

    @Test
    void cnpjJaCadastradoEvitaConsultasExternas() {
        when(oscRepository.existsByCnpj(any())).thenReturn(true);

        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> service.create(dados(), usuario, null));

        assertEquals(409, erro.getStatusCode().value());
        verifyNoInteractions(validacaoCnpjOscService, geocodingService, persistenciaService);
    }

    @Test
    void exigeUsuarioAutenticadoAntesDeIniciarFluxo() {
        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> service.create(dados(), null, null));

        assertEquals(401, erro.getStatusCode().value());
        verifyNoInteractions(oscRepository, validacaoCnpjOscService, geocodingService, persistenciaService);
    }

    private RequestOscDTO dados() {
        return new RequestOscDTO(
                CNPJ,
                Set.of(CausaSocial.EDUCACAO),
                Set.of(4),
                "Instituto Exemplo",
                "Apoio à educação",
                "Descrição institucional completa",
                2010,
                "contato@exemplo.org.br",
                null,
                null,
                null,
                null,
                null,
                null,
                "cidade",
                "Brasil",
                "São Paulo",
                "São Paulo",
                "01000-000",
                "Centro",
                "Rua Exemplo",
                "10",
                null,
                Set.of("segunda"),
                null,
                null,
                false,
                true,
                null,
                true,
                null,
                null,
                null,
                null,
                null);
    }
}
