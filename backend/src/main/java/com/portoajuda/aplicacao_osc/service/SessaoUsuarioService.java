
package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.SessaoUsuarioRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessaoUsuarioService {

    private static final Duration DURACAO_TEMPORARIA = Duration.ofHours(12);

    private static final Duration DURACAO_INATIVIDADE = Duration.ofDays(30);

    private static final Duration DURACAO_MAXIMA_PERSISTENTE = Duration.ofDays(180);

    private final SessaoUsuarioRepository sessaoRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Cria uma sessão após o login.
     * Todas as sessões recebem refresh token.
     * O banco armazena apenas o hash desse token.
     */
    @Transactional
    public SessaoCriada criarSessao(
            Usuario usuario,
            boolean lembrarDeMim) {

        if (usuario == null || !usuario.isEnabled()) {
            throw new IllegalArgumentException(
                    "Não foi possível criar uma sessão para este usuário.");
        }

        Instant agora = Instant.now();

        String refreshToken = gerarTokenAleatorio();
        String tokenHash = calcularHash(refreshToken);

        Instant expiracao = lembrarDeMim
                ? agora.plus(DURACAO_MAXIMA_PERSISTENTE)
                : agora.plus(DURACAO_TEMPORARIA);

        SessaoUsuario sessao = new SessaoUsuario(
                usuario,
                tokenHash,
                agora,
                expiracao,
                lembrarDeMim);

        SessaoUsuario salva = sessaoRepository.save(sessao);

        return new SessaoCriada(salva, refreshToken);
    }

    /**
     * Valida uma sessão usando o refresh token e,
     * se válida, rotaciona o token.
     */
    @Transactional
    public Optional<SessaoCriada> renovarSessao(
            String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }

        String hash = calcularHash(refreshToken);

        Optional<SessaoUsuario> resultado = sessaoRepository.findByTokenHash(hash);

        if (resultado.isEmpty()) {
            return Optional.empty();
        }

        SessaoUsuario sessao = resultado.get();
        Instant agora = Instant.now();

        boolean expirada = !sessao.getExpiraEm().isAfter(agora);

        boolean inativa = sessao.isLembrarDeMim()
                && !sessao.getUltimaAtividadeEm()
                        .plus(DURACAO_INATIVIDADE)
                        .isAfter(agora);

        boolean usuarioDesativado = !sessao.getUsuario().isEnabled();

        if (sessao.isRevogada()
                || expirada
                || inativa
                || usuarioDesativado) {

            if (!sessao.isRevogada()) {
                sessao.setRevogadaEm(agora);
                sessaoRepository.save(sessao);
            }

            return Optional.empty();
        }

        // Gera um novo token; o anterior deixa de ser válido.
        String novoRefreshToken = gerarTokenAleatorio();

        sessao.setTokenHash(calcularHash(novoRefreshToken));
        sessao.setUltimaAtividadeEm(agora);

        SessaoUsuario salva = sessaoRepository.save(sessao);

        return Optional.of(
                new SessaoCriada(salva, novoRefreshToken));
    }

    /**
     * Verifica se a sessão associada ao JWT continua válida.
     */
    @Transactional
    public Optional<SessaoUsuario> buscarSessaoValida(
            Long sessaoId) {

        if (sessaoId == null) {
            return Optional.empty();
        }

        Optional<SessaoUsuario> resultado = sessaoRepository.findById(sessaoId);

        if (resultado.isEmpty()) {
            return Optional.empty();
        }

        SessaoUsuario sessao = resultado.get();
        Instant agora = Instant.now();

        boolean expirada = !sessao.getExpiraEm().isAfter(agora);

        boolean inativa = sessao.isLembrarDeMim()
                && !sessao.getUltimaAtividadeEm()
                        .plus(DURACAO_INATIVIDADE)
                        .isAfter(agora);

        boolean usuarioDesativado = !sessao.getUsuario().isEnabled();

        if (sessao.isRevogada()
                || expirada
                || inativa
                || usuarioDesativado) {

            if (!sessao.isRevogada()) {
                sessao.setRevogadaEm(agora);
                sessaoRepository.save(sessao);
            }

            return Optional.empty();
        }

        return Optional.of(sessao);
    }

    /**
     * Revoga uma sessão específica, por exemplo, no logout.
     */
    @Transactional
    public void revogarSessao(Long sessaoId) {

        if (sessaoId == null) {
            return;
        }

        sessaoRepository.findByIdForUpdate(sessaoId).ifPresent(sessao -> {
            if (!sessao.isRevogada()) {
                sessao.setRevogadaEm(Instant.now());
                sessaoRepository.save(sessao);
            }
        });
    }

    private String gerarTokenAleatorio() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String calcularHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8));

            return java.util.HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 não está disponível.",
                    e);
        }
    }

    public record SessaoCriada(
            SessaoUsuario sessao,
            String refreshToken) {
    }
}
