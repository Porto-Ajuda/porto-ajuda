
package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.CodigoAutenticacaoRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LimpezaCadastrosService {

    private static final Logger log = LoggerFactory.getLogger(LimpezaCadastrosService.class);

    private final UsuarioRepository usuarioRepository;
    private final CodigoAutenticacaoRepository codigoAutenticacaoRepository;

    /**
     * Exclui usuários que ainda não confirmaram o e-mail
     * e ultrapassaram o limite de tempo.
     */
    @Transactional
    public int excluirCadastrosPendentes() {

        // TESTE: considera cadastros com mais de 5 minutos.
        // LocalDateTime limite = LocalDateTime.now().minusMinutes(5);

        // PRODUÇÃO: altere para LocalDateTime.now().minusHours(24).
        LocalDateTime limite = LocalDateTime.now().minusHours(24);

        List<Usuario> pendentes = usuarioRepository.findByEmailVerificadoFalseAndDataCriacaoBefore(limite);

        int excluidos = 0;

        for (Usuario usuario : pendentes) {
            Integer usuarioId = usuario.getId();

            // Remove os códigos de confirmação associados.
            codigoAutenticacaoRepository.deleteByUsuarioId(usuarioId);

            // Remove a associação entre usuário e funções.
            usuarioRepository.removerAssociacoesRoles(usuarioId);

            // Exclui o usuário pendente.
            usuarioRepository.delete(usuario);

            excluidos++;
        }

        return excluidos;
    }

    @Transactional
    // Executa automaticamente a limpeza a cada minuto durante os testes.
    // @Scheduled(cron = "0 * * * * *", zone = "America/Sao_Paulo")

    // Executa automaticamente a limpeza a cada hora durante a produção.
    @Scheduled(cron = "0 0 * * * *", zone = "America/Sao_Paulo")
    public void executarLimpezaAutomatica() {

        try {
            int excluidos = excluirCadastrosPendentes();

            log.info(
                    "Limpeza automática concluída. Cadastros excluídos: {}",
                    excluidos);

        } catch (Exception e) {
            log.error(
                    "Falha na limpeza automática de cadastros pendentes.",
                    e);
        }
    }
}
