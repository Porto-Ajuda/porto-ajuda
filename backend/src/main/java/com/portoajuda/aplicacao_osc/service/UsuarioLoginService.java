package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestLoginDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.utils.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps password validation and session issuance serialized with password changes. */
@Service
@RequiredArgsConstructor
public class UsuarioLoginService {
    private final UsuarioRepository usuarioRepository;
    private final AutenticacaoAbusoUsuarioService autenticacaoAbuso;
    private final SessaoUsuarioService sessaoUsuarioService;
    private final JwtService jwtService;

    @Transactional
    public LoginOutcome autenticar(RequestLoginDTO request, Email email) {
        Usuario usuario = usuarioRepository.findByEmailForUpdate(email).orElse(null);
        boolean contaBloqueada = autenticacaoAbuso.contaLoginBloqueada(email.valor());
        boolean senhaCorreta = autenticacaoAbuso.verificarSenhaMesmoParaContaInexistente(
                usuario, request.senha());

        if (usuario == null) return LoginOutcome.rejeitado();
        if (!senhaCorreta) {
            if (!contaBloqueada) autenticacaoAbuso.registrarFalhaLogin(email.valor());
            return LoginOutcome.rejeitado();
        }
        // Do not let an attacker keep the legitimate account owner locked out.
        // The account throttle rejects incorrect passwords; a correct password
        // remains sufficient to authenticate while that temporary throttle is active.
        if (!usuario.isEmailVerificado() || !usuario.isEnabled()) {
            return LoginOutcome.rejeitado();
        }

        SessaoUsuarioService.SessaoCriada sessaoCriada = sessaoUsuarioService.criarSessao(
                usuario, request.lembrarDeMim());
        SessaoUsuario sessao = sessaoCriada.sessao();
        String accessToken = jwtService.generateToken(usuario, sessao.getId());
        ResponseUsuarioDTO dadosUsuario = new ResponseUsuarioDTO(
                usuario.getCpf().valor(), usuario.getNome(), usuario.getNomeSocial(),
                usuario.getDataNascimento().toString(), usuario.getEmail().valor(), usuario.getTelefone());

        return LoginOutcome.autenticado(new UsuarioService.LoginResultado(accessToken, sessaoCriada.refreshToken(),
                request.lembrarDeMim(), dadosUsuario, sessao.getExpiraEm()));
    }

    public record LoginOutcome(UsuarioService.LoginResultado resultado, boolean autenticado) {
        static LoginOutcome autenticado(UsuarioService.LoginResultado resultado) {
            return new LoginOutcome(resultado, true);
        }

        static LoginOutcome rejeitado() {
            return new LoginOutcome(null, false);
        }
    }
}
