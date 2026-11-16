package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional reactivation core; the public wrapper applies abuse limits before this transaction. */
@Service
@RequiredArgsConstructor
public class UsuarioReativacaoService {
    private static final ZoneId ZONA_APLICACAO = ZoneId.of("America/Sao_Paulo");

    private final UsuarioRepository usuarioRepository;
    private final AutenticacaoAbusoUsuarioService autenticacaoAbuso;
    private final SessaoUsuarioService sessaoUsuarioService;

    @Transactional
    public boolean cancelar(Email email, String senha) {
        Usuario usuario = usuarioRepository.findByEmailForUpdate(email).orElse(null);
        boolean senhaCorreta = autenticacaoAbuso.verificarSenhaMesmoParaContaInexistente(usuario, senha);
        LocalDateTime agora = LocalDateTime.now(ZONA_APLICACAO);

        if (usuario == null || !senhaCorreta || usuario.isEnabled() || !usuario.isEmailVerificado()
                || usuario.getDataSolicitacaoExclusao() == null || usuario.getDataExclusaoAgendada() == null
                || usuario.getDataSolicitacaoExclusao().isAfter(agora)
                || !usuario.getDataExclusaoAgendada().equals(
                        UsuarioService.calcularDataExclusao(usuario.getDataSolicitacaoExclusao()))
                || !agora.isBefore(usuario.getDataExclusaoAgendada())) {
            return false;
        }

        usuario.setAtivo(true);
        usuario.setDataSolicitacaoExclusao(null);
        usuario.setDataExclusaoAgendada(null);
        sessaoUsuarioService.revogarSessoesDoUsuario(usuario.getId());
        return true;
    }
}
