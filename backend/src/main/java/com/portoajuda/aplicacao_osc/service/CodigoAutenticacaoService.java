package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.response.ResponseUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.CodigoAutenticacao;
import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.CodigoAutenticacaoRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class CodigoAutenticacaoService {

        private static final Duration VALIDADE = Duration.ofMinutes(5);

        private final CodigoAutenticacaoRepository codigoRepository;
        private final UsuarioRepository usuarioRepository;
        private final JwtService jwtService;
        private final SessaoUsuarioService sessaoUsuarioService;

        private final SecureRandom secureRandom = new SecureRandom();

        @Transactional
        public String criarCodigo(Usuario usuario) {
                byte[] bytes = new byte[32];
                secureRandom.nextBytes(bytes);

                String codigo = Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(bytes);

                String hash = gerarHash(codigo);

                CodigoAutenticacao registro = new CodigoAutenticacao(
                                hash,
                                usuario,
                                Instant.now().plus(VALIDADE));

                codigoRepository.save(registro);

                return codigo;
        }

        @Transactional
        public LoginPorCodigo trocarPorLogin(String codigo) {
                if (codigo == null || codigo.isBlank()) {
                        throw new BadCredentialsException(
                                        "Código inválido ou expirado");
                }

                String hash = gerarHash(codigo);

                int consumidos = codigoRepository.consumirSeValido(
                                hash,
                                Instant.now());

                if (consumidos != 1) {
                        throw new BadCredentialsException(
                                        "Código inválido ou expirado");
                }

                CodigoAutenticacao registro = codigoRepository
                                .findByCodigoHash(hash)
                                .orElseThrow(() -> new BadCredentialsException(
                                                "Código inválido ou expirado"));

                Usuario usuario = usuarioRepository.findById(
                                registro.getUsuario().getId())
                                .orElseThrow(() -> new BadCredentialsException(
                                                "Usuário não encontrado"));

                if (!usuario.isEmailVerificado() || !usuario.isEnabled()) {
                        throw new BadCredentialsException(
                                        "Não foi possível autenticar este usuário");
                }

                // Cria uma sessão temporária, sem "lembrar de mim".
                SessaoUsuarioService.SessaoCriada sessaoCriada = sessaoUsuarioService.criarSessao(usuario, false);

                SessaoUsuario sessao = sessaoCriada.sessao();
                String refreshToken = sessaoCriada.refreshToken();

                String accessToken = jwtService.generateToken(
                                usuario,
                                sessao.getId());

                ResponseUsuarioDTO dadosUsuario = new ResponseUsuarioDTO(
                                usuario.getCpf().valor(),
                                usuario.getNome(),
                                usuario.getNomeSocial(),
                                usuario.getDataNascimento().toString(),
                                usuario.getEmail().valor(),
                                usuario.getTelefone());

                return new LoginPorCodigo(
                                accessToken,
                                refreshToken,
                                sessao.getId(),
                                dadosUsuario);
        }

        private String gerarHash(String codigo) {
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");

                        byte[] hash = digest.digest(
                                        codigo.getBytes(StandardCharsets.UTF_8));

                        return HexFormat.of().formatHex(hash);

                } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException(
                                        "Não foi possível gerar o hash do código", e);
                }
        }

        public record LoginPorCodigo(
                        String accessToken,
                        String refreshToken,
                        Long sessaoId,
                        ResponseUsuarioDTO usuario) {
        }
}