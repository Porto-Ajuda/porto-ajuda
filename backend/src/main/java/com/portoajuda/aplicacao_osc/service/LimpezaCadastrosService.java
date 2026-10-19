
package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.CodigoAutenticacaoRepository;
import com.portoajuda.aplicacao_osc.repository.SessaoUsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LimpezaCadastrosService {

    private static final Logger log =
            LoggerFactory.getLogger(LimpezaCadastrosService.class);

    private final UsuarioRepository usuarioRepository;
    private final CodigoAutenticacaoRepository codigoAutenticacaoRepository;
    private final SessaoUsuarioRepository sessaoUsuarioRepository;

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

    // TESTES: executa a cada minuto.
    @Scheduled(cron = "0 * * * * *", zone = "America/Sao_Paulo")

    // PRODUÇÃO: substitua a anotação acima pela seguinte:
    // @Scheduled(cron = "0 0 * * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void executarLimpezaAutomatica() {
        log.info("Iniciando limpeza automática.");

        try {
            int sessoesExcluidas = excluirSessoesInvalidas();

            int cadastrosExcluidos = excluirCadastrosPendentes();

            log.info(
                    "Limpeza automática concluída. Sessões excluídas: {}; "
                            + "cadastros pendentes excluídos: {}",
                    sessoesExcluidas,
                    cadastrosExcluidos
            );

        } catch (Exception e) {
            log.error("Falha na limpeza automática.", e);
        }
    }
}
