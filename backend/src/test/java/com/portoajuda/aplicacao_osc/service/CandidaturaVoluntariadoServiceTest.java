package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestDecisaoCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestFinalizarVoluntariadoDTO;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class CandidaturaVoluntariadoServiceTest {

    @Mock private CandidaturasVoluntariadoRepository candidaturas;
    @Mock private VagaRepository vagas;
    @Mock private OscRepository oscs;
    @Mock private OscService oscService;
    @Mock private EntityManager entityManager;
    @Mock private VolunteerCertificateStorage certificateStorage;
    @InjectMocks private CandidaturaVoluntariadoService service;

    private Usuario applicant;
    private Usuario manager;
    private Osc osc;
    private Vaga vaga;
    private Post post;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "certificateStorage", certificateStorage);
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
        lenient().when(vagas.findById(41)).thenReturn(Optional.of(vaga));
    }

    @Test
    void listarMinhasUsesOnlyTheAuthenticatedApplicantId() {
        var page = new PageImpl<CandidaturasVoluntariado>(java.util.List.of(),
                org.springframework.data.domain.PageRequest.of(0, 10), 0);
        when(candidaturas.findAllByUsuario_IdOrderByDataCandidaturaDesc(
                eq(11), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        var result = service.listarMinhas(applicant,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
        verify(candidaturas).findAllByUsuario_IdOrderByDataCandidaturaDesc(
                eq(11), any(org.springframework.data.domain.Pageable.class));
        verify(candidaturas, never()).findAllByUsuario_IdOrderByDataCandidaturaDesc(
                eq(22), any(org.springframework.data.domain.Pageable.class));
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
    void reapplicationAfterApplicantCancellationRemainsAllowedButRejectedHistoryBlocksIt() {
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.existsByVaga_IdAndUsuario_IdAndStatusIn(eq(41), eq(11), anyCollection()))
                .thenAnswer(invocation -> {
                    java.util.Collection<StatusCandidatura> blocking = invocation.getArgument(2);
                    assertTrue(blocking.contains(StatusCandidatura.RECUSADA));
                    assertFalse(blocking.contains(StatusCandidatura.DESISTIU));
                    return false;
                });
        when(candidaturas.countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA)).thenReturn(0L);
        when(candidaturas.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.candidatar(41, null, applicant);

        assertEquals(StatusCandidatura.PENDENTE, created.status());
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
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.EDITOR);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.decidir(7, 99, new RequestDecisaoCandidaturaDTO(StatusCandidatura.APROVADA), manager));

        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(vagas, candidaturas);
    }

    @Test
    void editorAndMemberCannotReadCandidateManagementData() {
        for (CargoOsc cargo : new CargoOsc[] { CargoOsc.EDITOR, CargoOsc.MEMBRO }) {
            when(oscService.obterCargoUsuario(osc, manager)).thenReturn(cargo);
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.listarDaOsc(7, null, manager,
                            org.springframework.data.domain.PageRequest.of(0, 20)));
            assertEquals(403, error.getStatusCode().value());
        }
        verify(candidaturas, never()).listarPorOscVagaEStatus(anyInt(), any(), any(), any());
    }

    @Test
    void administratorCanReadCandidateManagementData() {
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.ADMINISTRADOR);
        when(candidaturas.listarPorOscVagaEStatus(eq(7), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        var result = service.listarDaOsc(7, null, manager,
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertTrue(result.isEmpty());
        verify(candidaturas).listarPorOscVagaEStatus(eq(7), isNull(), isNull(), any());
    }

    @Test
    void candidateManagementCanBeScopedToOneOpportunity() {
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.ADMINISTRADOR);
        when(vagas.findById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.listarPorOscVagaEStatus(eq(7), eq(41), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.listarDaOsc(7, 41, null, manager,
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertTrue(result.isEmpty());
        verify(candidaturas).listarPorOscVagaEStatus(eq(7), eq(41), isNull(), any());
    }

    @Test
    void candidateFilterCannotCrossNgoScope() {
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.ADMINISTRADOR);
        Vaga outside = new Vaga();
        Post outsidePost = new Post();
        Osc otherOsc = new Osc(); ReflectionTestUtils.setField(otherOsc, "id", 99);
        outsidePost.setOsc(otherOsc); outside.setPost(outsidePost);
        when(vagas.findById(55)).thenReturn(Optional.of(outside));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.listarDaOsc(7, 55, null, manager, org.springframework.data.domain.PageRequest.of(0,20)));

        assertEquals(404, failure.getStatusCode().value());
        verify(candidaturas, never()).listarPorOscVagaEStatus(anyInt(), any(), any(), any());
    }

    @Test
    void archivedOrganizationDoesNotExposeCandidateManagementData() {
        osc.setDataArquivamento(java.time.LocalDateTime.now());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.listarDaOsc(7, null, manager,
                        org.springframework.data.domain.PageRequest.of(0, 20)));

        assertEquals(404, error.getStatusCode().value());
        verify(candidaturas, never()).listarPorOscVagaEStatus(anyInt(), any(), any(), any());
    }

    @Test
    void aprovacaoNaoUltrapassaCapacidadeDaVaga() {
        CandidaturasVoluntariado candidatura = candidatura(StatusCandidatura.PENDENTE);
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
    void pendingApplicationsDoNotConsumePublicCapacity() {
        when(vagas.findById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA)).thenReturn(0L);

        assertTrue(service.disponibilidadePublica(41).aceitaCandidaturas());
        verify(candidaturas).countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA);
    }

    @Test
    void twoSlotsOneAcceptedAndOnePendingStillHasOnePublicSlot() {
        CandidaturasVoluntariado accepted = candidatura(StatusCandidatura.APROVADA);
        CandidaturasVoluntariado pending = candidatura(StatusCandidatura.PENDENTE);
        assertEquals(2, List.of(accepted, pending).size());
        when(vagas.findById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA)).thenReturn(1L);

        var availability = service.disponibilidadePublica(41);

        assertTrue(availability.aceitaCandidaturas());
        assertEquals(1, vaga.getQuantidadeVagas() - 1L);
        verify(candidaturas).countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA);
        verify(candidaturas, never()).countByVaga_IdAndStatus(41, StatusCandidatura.PENDENTE);
    }

    @Test
    void fullCapacityAlsoStopsNewApplications() {
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.countByVaga_IdAndStatus(41, StatusCandidatura.APROVADA)).thenReturn(2L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.candidatar(41, null, applicant));

        assertEquals(409, error.getStatusCode().value());
        verify(candidaturas, never()).save(any());
    }

    @Test
    void completionRecordsOnlySelectedAcceptedParticipantsAndClosesPendingApplications() {
        CandidaturasVoluntariado completed = candidatura(StatusCandidatura.APROVADA);
        CandidaturasVoluntariado notCompleted = candidatura(StatusCandidatura.APROVADA);
        ReflectionTestUtils.setField(notCompleted, "id", 100);
        CandidaturasVoluntariado pending = candidatura(StatusCandidatura.PENDENTE);
        ReflectionTestUtils.setField(pending, "id", 101);
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.ADMINISTRADOR);
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        when(candidaturas.findAllByVagaIdForUpdate(41)).thenReturn(List.of(completed, notCompleted, pending));
        var request = new RequestFinalizarVoluntariadoDTO(LocalDate.now().minusDays(2), LocalDate.now().minusDays(1),
                List.of(new RequestFinalizarVoluntariadoDTO.Participante(99, true, new BigDecimal("3.50"))));

        var result = service.finalizarVoluntariado(7, 41, request, manager);

        assertEquals(StatusCandidatura.CONCLUIDA, completed.getStatus());
        assertEquals(new BigDecimal("3.50"), completed.getHorasRealizadas());
        assertEquals(StatusCandidatura.CANCELADA_PELA_ONG, notCompleted.getStatus());
        assertEquals(StatusCandidatura.CANCELADA_PELA_ONG, pending.getStatus());
        assertEquals(StatusVaga.ENCERRADA, vaga.getStatus());
        assertNotNull(vaga.getDataConclusao());
        assertEquals(1, result.get("completedCount"));
        assertEquals(1, result.get("canceledPendingCount"));
        verify(candidaturas).saveAll(anyList());
        verify(vagas).save(vaga);
    }

    @Test
    void completionCannotBeSubmittedTwice() {
        vaga.setStatus(StatusVaga.ENCERRADA);
        vaga.setDataConclusao(java.time.Instant.now());
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.CRIADOR);
        when(vagas.findForUpdateById(41)).thenReturn(Optional.of(vaga));
        var request = new RequestFinalizarVoluntariadoDTO(LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.finalizarVoluntariado(7, 41, request, manager));

        assertEquals(409, error.getStatusCode().value());
        verify(candidaturas, never()).saveAll(anyList());
    }

    @Test
    void certificateAccessIsLimitedToTheParticipantOrAuthorizedNgoManager() {
        CandidaturasVoluntariado completed = candidatura(StatusCandidatura.CONCLUIDA);
        completed.setCertificadoObjectKey("certificates/7/99/test.pdf");
        when(oscs.findById(7)).thenReturn(Optional.of(osc));
        when(candidaturas.findByIdAndUsuario_Id(99, 11)).thenReturn(Optional.of(completed));
        when(certificateStorage.createShortLivedAccess(completed.getCertificadoObjectKey()))
                .thenReturn(new VolunteerCertificateStorage.SignedAccess("https://private.example/signed", java.time.Instant.now().plusSeconds(60)));

        assertEquals("https://private.example/signed", service.certificateUrl(99, applicant));
        verify(certificateStorage).createShortLivedAccess("certificates/7/99/test.pdf");

        when(candidaturas.findByIdAndUsuario_Id(99, 22)).thenReturn(Optional.empty());
        when(candidaturas.findById(99)).thenReturn(Optional.of(completed));
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.ADMINISTRADOR);
        assertEquals("https://private.example/signed", service.certificateUrl(99, manager));

        Usuario unrelated = mock(Usuario.class);
        when(unrelated.getId()).thenReturn(33);
        when(unrelated.isEnabled()).thenReturn(true);
        when(candidaturas.findByIdAndUsuario_Id(99, 33)).thenReturn(Optional.empty());
        when(candidaturas.findById(99)).thenReturn(Optional.of(completed));
        when(oscService.obterCargoUsuario(osc, unrelated)).thenReturn(CargoOsc.MEMBRO);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.certificateUrl(99, unrelated)).getStatusCode().value());
    }

    @Test
    void certificateUploadRequiresManagerAndValidPrivateFile() {
        CandidaturasVoluntariado completed = candidatura(StatusCandidatura.CONCLUIDA);
        when(oscService.obterCargoUsuario(osc, manager)).thenReturn(CargoOsc.ADMINISTRADOR);
        when(oscs.findById(7)).thenReturn(Optional.of(osc));
        when(candidaturas.findByIdAndVaga_Post_Osc_Id(99, 7)).thenReturn(Optional.of(completed));
        when(certificateStorage.store(eq(7), eq(99), eq("pdf"), eq("application/pdf"), any(byte[].class)))
                .thenReturn("certificates/7/99/sample.pdf");
        MockMultipartFile file = new MockMultipartFile("certificado", "certificado.pdf", "application/pdf",
                new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2d, 0x31 });

        service.uploadCertificate(7, 99, file, manager);

        assertEquals("certificates/7/99/sample.pdf", completed.getCertificadoObjectKey());
        verify(candidaturas).save(completed);
        completed.setCertificadoObjectKey(null);
        MockMultipartFile invalid = new MockMultipartFile("certificado", "fake.pdf", "application/pdf", new byte[] { 1, 2, 3 });
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.uploadCertificate(7, 99, invalid, manager)).getStatusCode().value());
    }

    @Test
    void finalizedApplicationCannotBeChangedAgain() {
        CandidaturasVoluntariado candidatura = candidatura(StatusCandidatura.RECUSADA);
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
