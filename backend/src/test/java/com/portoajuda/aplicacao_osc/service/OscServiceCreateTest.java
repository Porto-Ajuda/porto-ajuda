package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.dto.response.ConsultaCnpjDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OscServiceCreateTest {

        private static final String CNPJ = "11222333000181";

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
                                supabaseStorageService,
                                limpezaService,
                                org.mockito.Mockito.mock(com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService.class));

                usuario = new Usuario();
                ReflectionTestUtils.setField(usuario, "id", 25);
        }

        @Test
        void validaCnpjGeocodificaEEncaminhaParaPersistencia() {
                RequestOscDTO dados = dados();
                Coordenadas coordenadas = new Coordenadas(-23.55, -46.63);

                ResponseOscDTO resposta = new ResponseOscDTO(
                                81,
                                CNPJ,
                                "Instituto Exemplo",
                                CausaSocial.EDUCACAO,
                                Set.of(),
                                Set.of(CausaSocial.EDUCACAO),
                                Set.of(4));

                when(oscRepository.existsByCnpj(any())).thenReturn(false);
                when(validacaoCnpjOscService.validar(CNPJ)).thenReturn(
                                new ConsultaCnpjDTO(
                                                CNPJ, "Instituto Exemplo", null, "2010-01-01",
                                                "Ativa", "Associação privada", true, null));

                when(geocodingService.buscarCoordenadas(
                                "Brasil", "São Paulo", "01000-000",
                                "São Paulo", "Rua Exemplo", "10")).thenReturn(coordenadas);

                when(persistenciaService.salvar(dados, 25, coordenadas, null))
                                .thenReturn(resposta);

                ResponseOscDTO resultado = service.create(dados, usuario, null);

                assertEquals(81, resultado.id());
                assertEquals(Set.of(4), resultado.ods());

                verify(persistenciaService).salvar(dados, 25, coordenadas, null);
        }

        @Test
        void comparaRespostaExternaComCnpjAlfanumericoCanonico() {
                String cnpjAlfanumerico = "12ABC34501DE35";
                RequestOscDTO dados = dados(cnpjAlfanumerico);
                Coordenadas coordenadas = new Coordenadas(-23.55, -46.63);

                when(oscRepository.existsByCnpj(any())).thenReturn(false);
                when(validacaoCnpjOscService.validar(cnpjAlfanumerico)).thenReturn(
                                new ConsultaCnpjDTO(
                                                "12.ABC.345/01DE-35", "Instituto Exemplo", null,
                                                "2010-01-01", "Ativa", "Associação privada", true, null));
                when(geocodingService.buscarCoordenadas(
                                "Brasil", "São Paulo", "01000-000",
                                "São Paulo", "Rua Exemplo", "10")).thenReturn(coordenadas);
                when(persistenciaService.salvar(dados, 25, coordenadas, null))
                                .thenReturn(new ResponseOscDTO(
                                                82, cnpjAlfanumerico, "Instituto Exemplo",
                                                CausaSocial.EDUCACAO, Set.of(),
                                                Set.of(CausaSocial.EDUCACAO), Set.of(4)));

                ResponseOscDTO resultado = service.create(dados, usuario, null);

                assertEquals(82, resultado.id());
                verify(validacaoCnpjOscService).validar(cnpjAlfanumerico);
                verify(persistenciaService).salvar(dados, 25, coordenadas, null);
        }

        @Test
        void cnpjInelegivelNaoGeocodificaNemPersiste() {
                RequestOscDTO dados = dados();

                when(oscRepository.existsByCnpj(any())).thenReturn(false);
                when(validacaoCnpjOscService.validar(CNPJ)).thenReturn(
                                new ConsultaCnpjDTO(
                                                CNPJ, null, null, null,
                                                "Inapta", null, false, "O CNPJ está inapto."));

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
                                new ConsultaCnpjDTO(
                                                CNPJ, "Instituto Exemplo", null, "2010-01-01",
                                                "Ativa", "Associação privada", true, null));

                when(geocodingService.buscarCoordenadas(
                                anyString(), anyString(), anyString(),
                                anyString(), anyString(), anyString())).thenThrow(new ResponseStatusException(
                                                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                                                "Endereço não localizado."));

                assertThrows(
                                ResponseStatusException.class,
                                () -> service.create(dados, usuario, null));

                verifyNoInteractions(persistenciaService);
        }

        @Test
        void cnpjJaCadastradoEvitaConsultasExternas() {
                when(oscRepository.existsByCnpj(any())).thenReturn(true);

                ResponseStatusException erro = assertThrows(
                                ResponseStatusException.class,
                                () -> service.create(dados(), usuario, null));

                assertEquals(409, erro.getStatusCode().value());

                verifyNoInteractions(
                                validacaoCnpjOscService,
                                geocodingService,
                                persistenciaService);
        }

        @Test
        void exigeUsuarioAutenticadoAntesDeIniciarFluxo() {
                ResponseStatusException erro = assertThrows(
                                ResponseStatusException.class,
                                () -> service.create(dados(), null, null));

                assertEquals(401, erro.getStatusCode().value());

                verifyNoInteractions(
                                oscRepository,
                                validacaoCnpjOscService,
                                geocodingService,
                                persistenciaService);
        }

        @Test
        void listaSomenteOsVinculosDoUsuarioAutenticado() {
                List<OscUsuarioDTO> oscs = List.of(
                                new OscUsuarioDTO(
                                                81,
                                                "Instituto Exemplo",
                                                null,
                                                CargoOsc.CRIADOR,
                                                StatusOsc.INATIVA,
                                                false));

                Osc osc = new Osc();
                ReflectionTestUtils.setField(osc, "id", 81);
                osc.setNome("Instituto Exemplo");
                osc.setUsuario(usuario);
                OscMembros vinculo = new OscMembros();
                vinculo.setOsc(osc);
                vinculo.setUsuario(usuario);
                vinculo.setCargoOsc(CargoOsc.CRIADOR);
                when(membrosRepository.findAllByUsuario_IdOrderByOsc_NomeAsc(25))
                                .thenReturn(List.of(vinculo));
                when(oscRepository.findAllByUsuario_IdOrderByNomeAsc(25))
                                .thenReturn(List.of(osc));

                assertEquals(oscs, service.listarDoUsuario(usuario));

                verify(membrosRepository).findAllByUsuario_IdOrderByOsc_NomeAsc(25);
        }

        @Test
        void listaOscDoMembroQuandoContaCriadoraFoiExcluida() {
                Osc osc = new Osc();
                ReflectionTestUtils.setField(osc, "id", 82);
                osc.setNome("Instituto sem titular");
                osc.setUsuario(null);

                OscMembros vinculo = new OscMembros();
                vinculo.setOsc(osc);
                vinculo.setUsuario(usuario);
                vinculo.setCargoOsc(CargoOsc.ADMINISTRADOR);

                when(membrosRepository.findAllByUsuario_IdOrderByOsc_NomeAsc(25))
                                .thenReturn(List.of(vinculo));
                when(oscRepository.findAllByUsuario_IdOrderByNomeAsc(25))
                                .thenReturn(List.of());

                List<OscUsuarioDTO> resultado = service.listarDoUsuario(usuario);

                assertEquals(1, resultado.size());
                assertEquals(82, resultado.get(0).id());
                assertEquals(CargoOsc.ADMINISTRADOR, resultado.get(0).cargo());
        }

        private RequestOscDTO dados() {
                return dados(CNPJ);
        }

        private RequestOscDTO dados(String cnpj) {
                return new RequestOscDTO(
                                cnpj,
                                CausaSocial.EDUCACAO,
                                Set.of(),
                                null,
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
