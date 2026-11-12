package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentService;
import com.portoajuda.aplicacao_osc.admin.AdminRbacInvariantService;
import com.portoajuda.aplicacao_osc.entity.HistoricoStatusOsc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.OrigemDesativacao;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.TipoEventoHistoricoOsc;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.CodigoAutenticacaoRepository;
import com.portoajuda.aplicacao_osc.repository.DoacaoRepository;
import com.portoajuda.aplicacao_osc.repository.HistoricoStatusOscRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostCausasRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.PreferenciasAcessibilidadeRepository;
import com.portoajuda.aplicacao_osc.repository.SessaoUsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LimpezaServiceExclusaoTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CodigoAutenticacaoRepository codigoAutenticacaoRepository;
    @Mock private SessaoUsuarioRepository sessaoUsuarioRepository;
    @Mock private CandidaturasVoluntariadoRepository candidaturasRepository;
    @Mock private DoacaoRepository doacaoRepository;
    @Mock private HistoricoStatusOscRepository historicoStatusOscRepository;
    @Mock private OscRepository oscRepository;
    @Mock private OscMembrosRepository oscMembrosRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostCausasRepository postCausasRepository;
    @Mock private VagaRepository vagaRepository;
    @Mock private PreferenciasAcessibilidadeRepository preferenciasRepository;
    @Mock private AdminAssignmentService adminAssignmentService;
    @Mock private AdminRbacInvariantService adminRbacInvariantService;

    @InjectMocks
    private LimpezaService limpezaService;

    @BeforeEach
    void prepararDependenciasRbacDesabilitado() {
        lenient().when(adminAssignmentService.prepareForScheduledCleanup(any())).thenReturn(true);
    }

    @Test
    void contaElegivelRemoveDependenciasNaOrdemSemOngs() {
        Usuario usuario = usuarioVencido(42);
        prepararCandidata(usuario);
        when(oscRepository.findAllByUsuario_Id(42)).thenReturn(List.of());
        when(doacaoRepository.existsByUsuario_Id(42)).thenReturn(false);

        assertEquals(1, limpezaService.excluirContasComPrazoVencido());

        InOrder ordem = inOrder(
                candidaturasRepository,
                oscMembrosRepository,
                codigoAutenticacaoRepository,
                sessaoUsuarioRepository,
                preferenciasRepository,
                usuarioRepository,
                postRepository);
        ordem.verify(candidaturasRepository).excluirPorUsuarioId(42);
        ordem.verify(oscMembrosRepository).excluirPorUsuarioId(42);
        ordem.verify(codigoAutenticacaoRepository).deleteByUsuarioId(42);
        ordem.verify(sessaoUsuarioRepository).excluirPorUsuarioId(42);
        ordem.verify(preferenciasRepository).excluirPorUsuarioId(42);
        ordem.verify(usuarioRepository).removerAssociacoesRoles(42);
        ordem.verify(postRepository).desvincularAutorPorUsuarioId(42);
        ordem.verify(usuarioRepository).excluirPorIdSemCascade(42);
        verify(usuarioRepository, never()).delete(usuario);
    }

    @Test
    void contaComHistoricoFinanceiroDeDoadorPermaneceIntacta() {
        Usuario usuario = usuarioVencido(42);
        prepararCandidata(usuario);
        when(oscRepository.findAllByUsuario_Id(42)).thenReturn(List.of());
        when(doacaoRepository.existsByUsuario_Id(42)).thenReturn(true);

        assertEquals(0, limpezaService.excluirContasComPrazoVencido());

        verifyNoInteractions(candidaturasRepository, codigoAutenticacaoRepository,
                sessaoUsuarioRepository, oscMembrosRepository, postRepository,
                postCausasRepository, vagaRepository, preferenciasRepository);
        verify(usuarioRepository, never()).delete(usuario);
    }

    @Test
    void ongComDoacoesBloqueiaExclusaoDaContaEPreservaAOrganizacao() {
        Usuario usuario = usuarioVencido(42);
        Osc osc = mock(Osc.class);
        when(osc.getId()).thenReturn(77);
        prepararCandidata(usuario);
        when(oscRepository.findAllByUsuario_Id(42)).thenReturn(List.of(osc));
        when(doacaoRepository.existsByUsuario_Id(42)).thenReturn(false);
        when(doacaoRepository.existsByOsc_Id(77)).thenReturn(true);

        assertEquals(0, limpezaService.excluirContasComPrazoVencido());

        verify(oscRepository, never()).delete(osc);
        verify(usuarioRepository, never()).delete(usuario);
        verifyNoInteractions(candidaturasRepository, codigoAutenticacaoRepository,
                sessaoUsuarioRepository, oscMembrosRepository, postRepository,
                postCausasRepository, vagaRepository, preferenciasRepository);
    }

    @Test
    void doacaoVinculadaAPostDaOngTambemBloqueiaExclusaoParaPreservarHistorico() {
        Usuario usuario = usuarioVencido(42);
        Osc osc = mock(Osc.class);
        when(osc.getId()).thenReturn(77);
        prepararCandidata(usuario);
        when(oscRepository.findAllByUsuario_Id(42)).thenReturn(List.of(osc));
        when(doacaoRepository.existsByUsuario_Id(42)).thenReturn(false);
        when(doacaoRepository.existsByOsc_Id(77)).thenReturn(false);
        when(doacaoRepository.existsByPost_Osc_Id(77)).thenReturn(true);

        assertEquals(0, limpezaService.excluirContasComPrazoVencido());

        verify(oscRepository, never()).delete(osc);
        verify(usuarioRepository, never()).excluirPorIdSemCascade(42);
        verify(doacaoRepository, never()).delete(any());
    }

    @Test
    void contaAtivaNaoEExcluidaMesmoSeAConsultaRetornarCandidataDesatualizada() {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(42);
        when(usuario.isEnabled()).thenReturn(true);
        when(usuarioRepository
                .findByAtivoFalseAndDataSolicitacaoExclusaoIsNotNullAndDataExclusaoAgendadaLessThanEqual(
                        any(LocalDateTime.class)))
                .thenReturn(List.of(usuario));
        when(usuarioRepository.findByIdForUpdate(42)).thenReturn(Optional.of(usuario));

        assertEquals(0, limpezaService.excluirContasComPrazoVencido());

        verifyNoInteractions(oscRepository, doacaoRepository, candidaturasRepository,
                codigoAutenticacaoRepository, sessaoUsuarioRepository,
                oscMembrosRepository, postRepository, postCausasRepository,
                vagaRepository, preferenciasRepository);
        verify(usuarioRepository, never()).excluirPorIdSemCascade(42);
    }

    @Test
    void prazoAindaNaoVencidoNaoEExcluidoMesmoSeAConsultaEstiverDesatualizada() {
        Usuario usuario = mock(Usuario.class);
        LocalDateTime agora = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime solicitadaEm = agora.minusDays(1);

        when(usuario.getId()).thenReturn(42);
        when(usuario.getDataSolicitacaoExclusao()).thenReturn(solicitadaEm);
        when(usuario.getDataExclusaoAgendada()).thenReturn(solicitadaEm.plusMonths(6));
        when(usuario.isEnabled()).thenReturn(false);
        when(usuario.isEmailVerificado()).thenReturn(true);
        when(usuarioRepository
                .findByAtivoFalseAndDataSolicitacaoExclusaoIsNotNullAndDataExclusaoAgendadaLessThanEqual(
                        any(LocalDateTime.class)))
                .thenReturn(List.of(usuario));
        when(usuarioRepository.findByIdForUpdate(42)).thenReturn(Optional.of(usuario));

        assertEquals(0, limpezaService.excluirContasComPrazoVencido());

        verifyNoInteractions(oscRepository, doacaoRepository, candidaturasRepository,
                codigoAutenticacaoRepository, sessaoUsuarioRepository,
                oscMembrosRepository, postRepository, postCausasRepository,
                vagaRepository, preferenciasRepository);
        verify(usuarioRepository, never()).excluirPorIdSemCascade(42);
    }

    @Test
    void ongSemDoacoesEArquivadaSemApagarIdentidadeConteudoOuRestricao() {
        Usuario usuario = usuarioVencido(42);
        Osc osc = mock(Osc.class);
        when(osc.getId()).thenReturn(77);
        when(osc.isArquivada()).thenReturn(false);
        when(osc.getStatus()).thenReturn(StatusOsc.INATIVA);
        when(osc.getOrigemUltimaDesativacao()).thenReturn(OrigemDesativacao.PORTO_AJUDA);
        prepararCandidata(usuario);
        when(oscRepository.findAllByUsuario_Id(42)).thenReturn(List.of(osc));
        when(doacaoRepository.existsByUsuario_Id(42)).thenReturn(false);
        when(doacaoRepository.existsByOsc_Id(77)).thenReturn(false);

        assertEquals(1, limpezaService.excluirContasComPrazoVencido());

        verify(oscRepository, never()).delete(osc);
        verify(oscRepository, atLeastOnce()).save(osc);
        verify(oscMembrosRepository).excluirPorUsuarioId(42);
        verify(oscMembrosRepository, never()).excluirPorOscId(77);
        verify(candidaturasRepository, never()).excluirPorOscId(77);
        verify(postRepository, never()).excluirPorOscId(77);
        verify(historicoStatusOscRepository).save(argThat(historico ->
                historico.getTipoEvento() == TipoEventoHistoricoOsc.ARQUIVAMENTO
                        && historico.getStatusAnterior() == StatusOsc.INATIVA
                        && historico.getStatusNovo() == StatusOsc.INATIVA
                        && historico.getOrigemDesativacaoAnterior() == OrigemDesativacao.PORTO_AJUDA));
    }

    @Test
    void gestorArquivaOngModeradaSemApagarCnpjRestricaoOuHistorico() {
        Usuario gestor = mock(Usuario.class);
        when(gestor.getId()).thenReturn(15);
        Osc osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 77);
        osc.setStatus(StatusOsc.INATIVA);
        osc.setOrigemUltimaDesativacao(OrigemDesativacao.PORTO_AJUDA);
        LocalDateTime dataDesativacao = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(4);
        osc.setDataUltimaDesativacao(dataDesativacao);
        osc.setDesativadoPorIdSnapshot(9);
        osc.setMotivoUltimaDesativacao("Restrição da plataforma");
        osc.setCnpj(new com.portoajuda.aplicacao_osc.utils.Cnpj("11222333000181"));
        when(doacaoRepository.existsByOsc_Id(77)).thenReturn(false);
        when(doacaoRepository.existsByPost_Osc_Id(77)).thenReturn(false);

        limpezaService.excluirOscGerenciada(osc, gestor, "ADMINISTRADOR");

        assertTrue(osc.isArquivada());
        assertEquals(StatusOsc.INATIVA, osc.getStatus());
        assertEquals(OrigemDesativacao.PORTO_AJUDA, osc.getOrigemUltimaDesativacao());
        assertEquals(dataDesativacao, osc.getDataUltimaDesativacao());
        assertEquals("11222333000181", osc.getCnpj().getCnpj());
        assertSame(gestor, osc.getArquivadaPor());
        assertNotNull(osc.getDataArquivamento());
        verify(oscRepository, never()).delete(osc);
        verify(oscRepository).save(osc);
        verify(postRepository, never()).excluirPorOscId(77);
        verify(candidaturasRepository, never()).excluirPorOscId(77);
        verify(oscMembrosRepository, never()).excluirPorOscId(77);
        verify(historicoStatusOscRepository).save(argThat(historico ->
                historico.getTipoEvento() == TipoEventoHistoricoOsc.ARQUIVAMENTO
                        && historico.getOrigemDesativacaoAnterior() == OrigemDesativacao.PORTO_AJUDA
                        && historico.getDataDesativacaoAnterior().equals(dataDesativacao)
                        && historico.getDesativadorAnteriorIdSnapshot() == 9
                        && "Restrição da plataforma".equals(historico.getMotivoDesativacaoAnterior())
                        && "ADMINISTRADOR".equals(historico.getAutoridadeAtor())));
    }

    @Test
    void arquivamentoDeOrigemIndeterminadaNaoAlteraRestricaoDeReativacao() {
        Usuario gestor = mock(Usuario.class);
        when(gestor.getId()).thenReturn(15);
        Osc osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 78);
        osc.setStatus(StatusOsc.INATIVA);
        osc.setOrigemUltimaDesativacao(OrigemDesativacao.INDETERMINADA);
        when(doacaoRepository.existsByOsc_Id(78)).thenReturn(false);
        when(doacaoRepository.existsByPost_Osc_Id(78)).thenReturn(false);

        limpezaService.excluirOscGerenciada(osc, gestor, "CRIADOR");

        assertTrue(osc.isArquivada());
        assertEquals(OrigemDesativacao.INDETERMINADA, osc.getOrigemUltimaDesativacao());
        verify(oscRepository, never()).delete(osc);
        verify(historicoStatusOscRepository).save(argThat(historico ->
                historico.getTipoEvento() == TipoEventoHistoricoOsc.ARQUIVAMENTO
                        && historico.getOrigemDesativacaoAnterior() == OrigemDesativacao.INDETERMINADA));
    }

    @Test
    void ongSemRestricaoAdministrativaSegueFluxoDeArquivamentoSemExclusaoFisica() {
        Usuario gestor = mock(Usuario.class);
        when(gestor.getId()).thenReturn(15);
        Osc osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 79);
        osc.setStatus(StatusOsc.INATIVA);
        osc.setOrigemUltimaDesativacao(OrigemDesativacao.ONG);
        when(doacaoRepository.existsByOsc_Id(79)).thenReturn(false);
        when(doacaoRepository.existsByPost_Osc_Id(79)).thenReturn(false);

        limpezaService.excluirOscGerenciada(osc, gestor, "CRIADOR");

        assertTrue(osc.isArquivada());
        assertEquals(OrigemDesativacao.ONG, osc.getOrigemUltimaDesativacao());
        assertEquals("Arquivamento solicitado pelo responsável da organização.",
                osc.getMotivoArquivamento());
        verify(oscRepository, never()).delete(osc);
        verify(historicoStatusOscRepository).save(argThat(historico ->
                historico.getTipoEvento() == TipoEventoHistoricoOsc.ARQUIVAMENTO
                        && historico.getOrigemDesativacaoAnterior() == OrigemDesativacao.ONG
                        && "Arquivamento solicitado pelo responsável da organização."
                                .equals(historico.getMotivo())));
    }

    private Usuario usuarioVencido(Integer id) {
        Usuario usuario = mock(Usuario.class);
        LocalDateTime agora = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime solicitadaEm = agora.minusMonths(7);

        when(usuario.getId()).thenReturn(id);
        when(usuario.getDataSolicitacaoExclusao()).thenReturn(solicitadaEm);
        when(usuario.getDataExclusaoAgendada()).thenReturn(solicitadaEm.plusMonths(6));
        when(usuario.isEnabled()).thenReturn(false);
        when(usuario.isEmailVerificado()).thenReturn(true);
        return usuario;
    }

    private void prepararCandidata(Usuario usuario) {
        when(usuarioRepository
                .findByAtivoFalseAndDataSolicitacaoExclusaoIsNotNullAndDataExclusaoAgendadaLessThanEqual(
                        any(LocalDateTime.class)))
                .thenReturn(List.of(usuario));
        when(usuarioRepository.findByIdForUpdate(usuario.getId()))
                .thenReturn(Optional.of(usuario));
    }
}
