package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.CodigoAutenticacaoRepository;
import com.portoajuda.aplicacao_osc.repository.DoacaoRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostCausasRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.PreferenciasAcessibilidadeRepository;
import com.portoajuda.aplicacao_osc.repository.SessaoUsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LimpezaServiceExclusaoTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CodigoAutenticacaoRepository codigoAutenticacaoRepository;
    @Mock private SessaoUsuarioRepository sessaoUsuarioRepository;
    @Mock private CandidaturasVoluntariadoRepository candidaturasRepository;
    @Mock private DoacaoRepository doacaoRepository;
    @Mock private OscRepository oscRepository;
    @Mock private OscMembrosRepository oscMembrosRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostCausasRepository postCausasRepository;
    @Mock private VagaRepository vagaRepository;
    @Mock private PreferenciasAcessibilidadeRepository preferenciasRepository;

    @InjectMocks
    private LimpezaService limpezaService;

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
    void ongSemDoacoesRemoveDependenciasAntesDaOrganizacao() {
        Usuario usuario = usuarioVencido(42);
        Osc osc = mock(Osc.class);
        when(osc.getId()).thenReturn(77);
        prepararCandidata(usuario);
        when(oscRepository.findAllByUsuario_Id(42)).thenReturn(List.of(osc));
        when(doacaoRepository.existsByUsuario_Id(42)).thenReturn(false);
        when(doacaoRepository.existsByOsc_Id(77)).thenReturn(false);

        assertEquals(1, limpezaService.excluirContasComPrazoVencido());

        InOrder ordem = inOrder(
                candidaturasRepository,
                vagaRepository,
                postCausasRepository,
                postRepository,
                oscMembrosRepository,
                oscRepository);
        ordem.verify(candidaturasRepository).excluirPorOscId(77);
        ordem.verify(vagaRepository).excluirPorOscId(77);
        ordem.verify(postCausasRepository).excluirPorOscId(77);
        ordem.verify(postRepository).excluirPorOscId(77);
        ordem.verify(oscMembrosRepository).excluirPorOscId(77);
        ordem.verify(oscRepository).delete(osc);
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
