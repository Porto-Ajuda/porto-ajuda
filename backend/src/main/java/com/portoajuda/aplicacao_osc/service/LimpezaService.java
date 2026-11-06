
package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.HistoricoStatusOsc;
import com.portoajuda.aplicacao_osc.enums.OrigemDesativacao;
import com.portoajuda.aplicacao_osc.enums.TipoEventoHistoricoOsc;
import com.portoajuda.aplicacao_osc.repository.CodigoAutenticacaoRepository;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.DoacaoRepository;
import com.portoajuda.aplicacao_osc.repository.HistoricoStatusOscRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.PreferenciasAcessibilidadeRepository;
import com.portoajuda.aplicacao_osc.repository.SessaoUsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LimpezaService {

    private static final ZoneId ZONA_APLICACAO = ZoneId.of("America/Sao_Paulo");

    private static final Logger log =
            LoggerFactory.getLogger(LimpezaService.class);

    private final UsuarioRepository usuarioRepository;
    private final CodigoAutenticacaoRepository codigoAutenticacaoRepository;
    private final SessaoUsuarioRepository sessaoUsuarioRepository;
    private final CandidaturasVoluntariadoRepository candidaturasRepository;
    private final DoacaoRepository doacaoRepository;
    private final HistoricoStatusOscRepository historicoStatusOscRepository;
    private final OscRepository oscRepository;
    private final OscMembrosRepository oscMembrosRepository;
    private final PostRepository postRepository;
    private final PreferenciasAcessibilidadeRepository preferenciasRepository;

    private LocalDate ultimoAvisoDependenciasFinanceiras;

    @Transactional
    public int excluirCadastrosPendentes() {
        LocalDateTime limite = LocalDateTime.now().minusHours(24);

        List<Usuario> pendentes =
                usuarioRepository.findByEmailVerificadoFalseAndDataCriacaoBefore(limite);

        int excluidos = 0;

        for (Usuario usuario : pendentes) {
            Integer usuarioId = usuario.getId();

            codigoAutenticacaoRepository.deleteByUsuarioId(usuarioId);
            usuarioRepository.removerAssociacoesRoles(usuarioId);
            usuarioRepository.delete(usuario);

            excluidos++;
        }

        return excluidos;
    }

    @Transactional
    public int excluirSessoesInvalidas() {
        Instant agora = Instant.now();
        Instant limiteInatividade =
                agora.minusSeconds(30L * 24 * 60 * 60);

        return sessaoUsuarioRepository.excluirSessoesInvalidas(
                agora,
                limiteInatividade
        );
    }

    @Transactional
    public int excluirContasComPrazoVencido() {
        LocalDateTime agora = LocalDateTime.now(ZONA_APLICACAO);
        List<Usuario> candidatas = usuarioRepository
                .findByAtivoFalseAndDataSolicitacaoExclusaoIsNotNullAndDataExclusaoAgendadaLessThanEqual(agora);

        int excluidas = 0;
        int bloqueadasPorHistoricoFinanceiro = 0;

        for (Usuario candidata : candidatas) {
            Usuario usuario = usuarioRepository.findByIdForUpdate(candidata.getId())
                    .orElse(null);

            if (!elegivelParaExclusao(usuario, agora)) {
                continue;
            }

            Integer usuarioId = usuario.getId();
            List<Osc> oscsDoUsuario = oscRepository.findAllByUsuario_Id(usuarioId);

            boolean possuiDoacaoComoDoador = doacaoRepository.existsByUsuario_Id(usuarioId);
            boolean possuiDoacaoEmOngPropria = oscsDoUsuario.stream()
                    .anyMatch(osc -> doacaoRepository.existsByOsc_Id(osc.getId())
                            || doacaoRepository.existsByPost_Osc_Id(osc.getId()));

            if (possuiDoacaoComoDoador || possuiDoacaoEmOngPropria) {
                bloqueadasPorHistoricoFinanceiro++;
                continue;
            }

            candidaturasRepository.excluirPorUsuarioId(usuarioId);

            for (Osc osc : oscsDoUsuario) {
                arquivarOsc(osc, usuario, "EXCLUSAO_CONTA", "Exclusão da conta do criador");
                osc.setUsuario(null);
                oscRepository.save(osc);
            }

            oscMembrosRepository.excluirPorUsuarioId(usuarioId);
            codigoAutenticacaoRepository.deleteByUsuarioId(usuarioId);
            sessaoUsuarioRepository.excluirPorUsuarioId(usuarioId);
            preferenciasRepository.excluirPorUsuarioId(usuarioId);
            usuarioRepository.removerAssociacoesRoles(usuarioId);

            // Mantém publicações de outras ONGs e remove apenas o vínculo autoral.
            postRepository.desvincularAutorPorUsuarioId(usuarioId);

            // Evita que orphanRemoval em Usuario.posts apague publicações de outras ONGs.
            usuarioRepository.excluirPorIdSemCascade(usuarioId);
            excluidas++;
        }

        registrarBloqueioFinanceiroSeNecessario(bloqueadasPorHistoricoFinanceiro);
        return excluidas;
    }

    private boolean elegivelParaExclusao(Usuario usuario, LocalDateTime agora) {
        if (usuario == null
                || usuario.isEnabled()
                || !usuario.isEmailVerificado()
                || usuario.getDataSolicitacaoExclusao() == null
                || usuario.getDataExclusaoAgendada() == null
                || usuario.getDataExclusaoAgendada().isAfter(agora)) {
            return false;
        }

        return !usuario.getDataSolicitacaoExclusao().isAfter(agora)
                && usuario.getDataExclusaoAgendada().equals(
                        usuario.getDataSolicitacaoExclusao().plusMonths(6));
    }

    @Transactional
    public void excluirOscGerenciada(Osc osc, Usuario ator, String autoridadeAtor) {
        Integer oscId = osc.getId();
        if (doacaoRepository.existsByOsc_Id(oscId)
                || doacaoRepository.existsByPost_Osc_Id(oscId)) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "A organização possui histórico de doações e não pode ser excluída.");
        }

        arquivarOsc(
                osc,
                ator,
                autoridadeAtor,
                "Arquivamento solicitado pelo responsável da organização.");
    }

    private void arquivarOsc(
            Osc osc,
            Usuario ator,
            String autoridadeAtor,
            String motivo) {
        if (osc.isArquivada()) {
            throw new ResponseStatusException(CONFLICT, "A organização já está arquivada.");
        }

        LocalDateTime agora = LocalDateTime.now(ZONA_APLICACAO);
        LocalDateTime dataDesativacaoAnterior = osc.getDataUltimaDesativacao();
        LocalDateTime dataArquivamentoAnterior = osc.getDataArquivamento();
        Usuario arquivadorAnterior = osc.getArquivadaPor();
        Integer arquivadorAnteriorIdSnapshot = osc.getArquivadaPorIdSnapshot();
        String motivoArquivamentoAnterior = osc.getMotivoArquivamento();
        osc.setDataArquivamento(agora);
        osc.setArquivadaPor(ator);
        osc.setArquivadaPorIdSnapshot(ator == null ? null : ator.getId());
        osc.setMotivoArquivamento(motivo);
        oscRepository.save(osc);

        historicoStatusOscRepository.save(HistoricoStatusOsc.registrar(
                osc,
                osc.getStatus(),
                osc.getStatus(),
                OrigemDesativacao.ONG,
                TipoEventoHistoricoOsc.ARQUIVAMENTO,
                osc.getOrigemUltimaDesativacao(),
                dataDesativacaoAnterior,
                osc.getDesativadoPor(),
                osc.getDesativadoPorIdSnapshot(),
                osc.getMotivoUltimaDesativacao(),
                dataArquivamentoAnterior,
                arquivadorAnterior,
                arquivadorAnteriorIdSnapshot,
                motivoArquivamentoAnterior,
                ator,
                autoridadeAtor,
                motivo));
    }

    private void registrarBloqueioFinanceiroSeNecessario(int quantidade) {
        if (quantidade == 0) {
            return;
        }

        LocalDate hoje = LocalDate.now(ZONA_APLICACAO);
        if (hoje.equals(ultimoAvisoDependenciasFinanceiras)) {
            return;
        }

        ultimoAvisoDependenciasFinanceiras = hoje;
        log.warn(
                "Exclusão definitiva adiada para {} conta(s) com histórico financeiro; "
                        + "a política de retenção precisa ser definida. Nenhum dado financeiro foi removido.",
                quantidade);
    }

    // Desativado por padrão. Habilitar somente de forma explícita no ambiente desejado.
    @Scheduled(cron = "${app.maintenance.cleanup-cron:-}", zone = "America/Sao_Paulo")
    @Transactional
    public void executarLimpezaAutomatica() {
        log.info("Iniciando limpeza automática.");

        try {
            int sessoesExcluidas = excluirSessoesInvalidas();

            int cadastrosExcluidos = excluirCadastrosPendentes();

            int contasExcluidas = excluirContasComPrazoVencido();

            Runnable registrarConclusao = () -> log.info(
                    "Limpeza automática concluída. Sessões excluídas: {}; "
                            + "cadastros pendentes excluídos: {}; contas encerradas: {}",
                    sessoesExcluidas,
                    cadastrosExcluidos,
                    contasExcluidas);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status == STATUS_COMMITTED) {
                                    registrarConclusao.run();
                                } else {
                                    log.error("Falha na limpeza automática; a transação foi revertida.");
                                }
                            }
                        });
            } else {
                registrarConclusao.run();
            }

        } catch (RuntimeException e) {
            log.error("Falha na limpeza automática; as alterações da execução serão revertidas.", e);
            throw e;
        }
    }
}
