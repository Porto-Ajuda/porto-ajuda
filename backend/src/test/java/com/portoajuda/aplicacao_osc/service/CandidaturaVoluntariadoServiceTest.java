package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestDecisaoCandidaturaDTO;
import com.portoajuda.aplicacao_osc.entity.CandidaturasVoluntariado;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.ModalidadeVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class CandidaturaVoluntariadoServiceTest {

    @Mock private CandidaturasVoluntariadoRepository candidaturas;
    @Mock private VagaRepository vagas;
    @Mock private OscRepository oscs;
    @Mock private OscService oscService;
    @InjectMocks private CandidaturaVoluntariadoService service;

    private Usuario applicant;
    private Usuario manager;
    private Osc osc;
    private Vaga vaga;
    private Post post;

    @BeforeEach
    void setup() {
        applicant = mock(Usuario.class);
        lenient().when(applicant.getId()).thenReturn(11);
        lenient().when(applicant.isEnabled()).thenReturn(true);
        manager = mock(Usuario.class);
        lenient().when(manager.getId()).thenReturn(22);
        lenient().when(manager.isEnabled()).thenReturn(true);
        osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 7);
        osc.setNome("ONG de teste");
        osc.setStatus(StatusOsc.ATIVA);
        lenient().when(oscs.findForUpdateById(7)).thenReturn(Optional.of(osc));

        post = new Post();
        ReflectionTestUtils.setField(post, "id", 31);
        post.setOsc(osc);
        post.setTipo(TipoPost.VOLUNTARIADO);
        post.setAtivo(true);
        post.setTitulo("Mutirão");

        vaga = new Vaga();
        ReflectionTestUtils.setField(vaga, "id", 41);
        vaga.setPost(post);
        vaga.setStatus(StatusVaga.ABERTA);
        vaga.setQuantidadeVagas(2);
        vaga.setModalidade(ModalidadeVoluntariado.PRESENCIAL);
        vaga.setCidade("Santos");
        vaga.setBairro("Centro");
        vaga.setCargaHoraria("4 horas semanais");
        vaga.setDataInicio(LocalDate.now());
        vaga.setDataFim(LocalDate.now().plusDays(20));
    }

    @Test
    void criaCandidaturaComUsuarioDaSessaoEDataDoServidor() {
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.existsByVaga_IdAndUsuario_IdAndStatusIn(eq(41), eq(11), anyCollection())).thenReturn(false);
        when(candidaturas.countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA)).thenReturn(0L);
        when(candidaturas.save(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.candidatar(41, new RequestCandidaturaDTO(" Posso ajudar "), applicant);

        ArgumentCaptor<CandidaturasVoluntariado> captor = ArgumentCaptor.forClass(CandidaturasVoluntariado.class);
        verify(candidaturas).save(captor.capture());
        assertEquals(11, captor.getValue().getUsuario().getId());
        assertSame(vaga, captor.getValue().getVaga());
        assertEquals("Mutirão", result.tituloOportunidade());
        assertEquals(StatusCandidatura.PENDENTE, result.status());
        assertEquals(LocalDate.now(), result.dataCandidatura());
        assertEquals("Posso ajudar", result.mensagem());
        verify(vagas).findForUpdateById(41);
    }

    @Test
    void bloqueiaCandidaturaDuplicadaSemCriarNovoHistorico() {
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.existsByVaga_IdAndUsuario_IdAndStatusIn(eq(41), eq(11), anyCollection())).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.candidatar(41, null, applicant));

        assertEquals(409, error.getStatusCode().value());
        verify(candidaturas, never()).save(any());
    }

    @Test
    void bloqueiaOportunidadeExpiradaOuInativa() {
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        vaga.setDataFim(LocalDate.now().minusDays(1));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.candidatar(41, null, applicant));

        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(candidaturas);
    }

    @Test
    void rejeitaCancelamentoDeCandidaturaDeOutraPessoaComMesmo404() {
        when(candidaturas.findByIdAndUsuario_Id(99, 11)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.cancelarMinha(99, applicant));

        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(vagas);
    }

    @Test
    void usuarioPodeCancelarSomenteCandidaturaPendente() {
        CandidaturasVoluntariado candidatura = candidatura(StatusCandidatura.PENDENTE);
        when(candidaturas.findByIdAndUsuario_Id(99, 11)).thenReturn(Optional.of(candidatura));
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.save(candidatura)).thenReturn(candidatura);

        var result = service.cancelarMinha(99, applicant);

        assertEquals(StatusCandidatura.DESISTIU, result.status());
    }

    @Test
    void membroEditorNaoPodeDecidirCandidaturas() {
        when(oscs.findById(7)).thenReturn(Optional.of(osc));
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.EDITOR);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.decidir(7, 99, new RequestDecisaoCandidaturaDTO(StatusCandidatura.APROVADA), manager));

        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(vagas, candidaturas);
    }

    @Test
    void aprovacaoNaoUltrapassaCapacidadeDaVaga() {
        CandidaturasVoluntariado candidatura = candidatura(StatusCandidatura.PENDENTE);
        when(oscs.findById(7)).thenReturn(Optional.of(osc));
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.ADMINISTRADOR);
        when(candidaturas.findByIdAndVaga_Post_Osc_Id(99, 7)).thenReturn(Optional.of(candidatura));
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA)).thenReturn(2L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.decidir(7, 99, new RequestDecisaoCandidaturaDTO(StatusCandidatura.APROVADA), manager));

        assertEquals(409, error.getStatusCode().value());
        verify(candidaturas, never()).save(any());
    }

    @Test
    void aprovacaoERejeicaoSaoFinaisEApenasCandidaturaPendentePodeSerDecidida() {
        CandidaturasVoluntariado candidatura = candidatura(StatusCandidatura.PENDENTE);
        when(oscs.findById(7)).thenReturn(Optional.of(osc));
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.CRIADOR);
        when(candidaturas.findByIdAndVaga_Post_Osc_Id(99, 7)).thenReturn(Optional.of(candidatura));
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA)).thenReturn(1L);
        when(candidaturas.save(candidatura)).thenReturn(candidatura);

        var result = service.decidir(7, 99,
                new RequestDecisaoCandidaturaDTO(StatusCandidatura.APROVADA), manager);

        assertEquals(StatusCandidatura.APROVADA, result.status());
        assertTrue(result.podeDecidir());
    }

    @Test
    void repositoryLocksTheVacancyRowForEveryCapacityDecision() throws Exception {
        var method = VagaRepository.class.getMethod("findForUpdateById", Integer.class);

        assertEquals(LockModeType.PESSIMISTIC_WRITE, method.getAnnotation(Lock.class).value());
    }

    @Test
    void viewerAvailabilityHidesClosedOrFullOpportunities() {
        when(vagas.findById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA)).thenReturn(2L);

        assertFalse(service.disponibilidadePublica(41).aceitaCandidaturas());

        vaga.setStatus(StatusVaga.ENCERRADA);
        assertFalse(service.disponibilidadePublica(41).aceitaCandidaturas());
        verify(candidaturas, times(1)).countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA);
    }

    @Test
    void fullCapacityAlsoStopsNewApplications() {
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.existsByVaga_IdAndUsuario_IdAndStatusIn(eq(41), eq(11), anyCollection())).thenReturn(false);
        when(candidaturas.countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA)).thenReturn(2L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.candidatar(41, null, applicant));

        assertEquals(409, error.getStatusCode().value());
        verify(candidaturas, never()).save(any());
    }

    @Test
    void finalizedApplicationCannotBeChangedAgain() {
        CandidaturasVoluntariado candidatura = candidatura(StatusCandidatura.RECUSADA);
        when(oscs.findById(7)).thenReturn(Optional.of(osc));
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.ADMINISTRADOR);
        when(candidaturas.findByIdAndVaga_Post_Osc_Id(99, 7)).thenReturn(Optional.of(candidatura));
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.decidir(7, 99,
                        new RequestDecisaoCandidaturaDTO(StatusCandidatura.APROVADA), manager));

        assertEquals(409, error.getStatusCode().value());
        verify(candidaturas, never()).save(any());
    }

    private CandidaturasVoluntariado candidatura(StatusCandidatura status) {
        CandidaturasVoluntariado candidatura = new CandidaturasVoluntariado();
        ReflectionTestUtils.setField(candidatura, "id", 99);
        candidatura.setVaga(vaga);
        candidatura.setUsuario(applicant);
        candidatura.setStatus(status);
        candidatura.setDataCandidatura(LocalDate.now());
        return candidatura;
    }
}
