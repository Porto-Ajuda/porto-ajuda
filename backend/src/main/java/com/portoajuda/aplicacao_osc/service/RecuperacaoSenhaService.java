package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.RecuperacaoSenha;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.RecuperacaoSenhaRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecuperacaoSenhaService {
    private static final Duration VALIDADE = Duration.ofMinutes(20);
    private static final Duration JANELA_LIMITE = Duration.ofHours(1);
    private static final int MAX_SOLICITACOES = 3;
    private static final int MAX_TENTATIVAS = 10;
    private static final String CHAVE_TENTATIVA = "0".repeat(64);

    private final RecuperacaoSenhaRepository recuperacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final ApplicationEventPublisher eventos;
    private final byte[] pepper;
    private final SecureRandom random = new SecureRandom();

    public RecuperacaoSenhaService(RecuperacaoSenhaRepository recuperacaoRepository,
            UsuarioRepository usuarioRepository, UsuarioService usuarioService,
            ApplicationEventPublisher eventos,
            @Value("${app.password-recovery.pepper:}") String pepper) {
        this.recuperacaoRepository = recuperacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.eventos = eventos;
        try {
            this.pepper = Base64.getUrlDecoder().decode(pepper == null ? "" : pepper.trim());
        } catch (IllegalArgumentException invalidEncoding) {
            throw new IllegalStateException("PASSWORD_RECOVERY_PEPPER deve ser Base64 URL-safe com pelo menos 32 bytes.");
        }
        if (this.pepper.length < 32) {
            throw new IllegalStateException("PASSWORD_RECOVERY_PEPPER deve ser Base64 URL-safe com pelo menos 32 bytes.");
        }
    }

    @Transactional
    public void solicitar(String emailInformado, String enderecoRemoto) {
        String email = normalizarEmail(emailInformado);
        String ipKey = chave(enderecoRemoto == null ? "unknown" : enderecoRemoto);
        String emailKey = chave(email);
        bloquearChaves(emailKey, ipKey);
        Instant now = Instant.now();
        recuperacaoRepository.deleteOlderThan(now.minus(Duration.ofDays(1)));

        Usuario usuario = usuarioRepository.findByEmailForUpdate(new Email(email)).orElse(null);
        boolean elegivel = usuario != null && usuario.isEnabled() && usuario.isEmailVerificado();
        long recente = recuperacaoRepository.countRecent(emailKey, ipKey, now.minus(JANELA_LIMITE));
        RecuperacaoSenha evento = new RecuperacaoSenha();
        evento.setUsuario(elegivel ? usuario : null);
        evento.setEmailKey(emailKey);
        evento.setIpKey(ipKey);
        evento.setSolicitadoEm(now);
        evento.setExpiraEm(now.plus(VALIDADE));

        if (recente < MAX_SOLICITACOES && elegivel) {
            recuperacaoRepository.invalidateForUser(usuario.getId(), now);
            String token = novoToken();
            evento.setTokenHash(hash(token));
            recuperacaoRepository.save(evento);
            eventos.publishEvent(new PasswordRecoveryRequested(usuario.getEmail().valor(), token));
            return;
        }
        // Também registra endereços desconhecidos para aplicar o mesmo limite sem revelar cadastro.
        recuperacaoRepository.save(evento);
    }

    @Transactional
    public boolean redefinir(String token, String novaSenha, String confirmacao, String enderecoRemoto) {
        String ipKey = chave(enderecoRemoto == null ? "unknown" : enderecoRemoto);
        recuperacaoRepository.lockRateKey(chaveBloqueio(ipKey));
        Instant now = Instant.now();
        long tentativasRecentes = recuperacaoRepository.countRecent(CHAVE_TENTATIVA, ipKey, now.minus(JANELA_LIMITE));
        if (tentativasRecentes >= MAX_TENTATIVAS) return false;
        RecuperacaoSenha tentativa = new RecuperacaoSenha();
        tentativa.setEmailKey(CHAVE_TENTATIVA);
        tentativa.setIpKey(ipKey);
        tentativa.setSolicitadoEm(now);
        tentativa.setExpiraEm(now.plus(VALIDADE));
        recuperacaoRepository.save(tentativa);
        if (token == null || token.length() > 128 || token.isBlank()
                || novaSenha == null || !novaSenha.equals(confirmacao)) return false;
        String tokenHash = hash(token);
        RecuperacaoSenha referencia = recuperacaoRepository.findByTokenHash(tokenHash).orElse(null);
        if (referencia == null || referencia.getUsuario() == null) return false;
        Usuario usuario = usuarioRepository.findByIdForUpdate(referencia.getUsuario().getId()).orElse(null);
        if (usuario == null || !usuario.isEnabled()) return false;
        RecuperacaoSenha credencial = recuperacaoRepository.findByTokenHashForUpdate(tokenHash).orElse(null);
        if (credencial == null || credencial.getConsumidoEm() != null
                || !credencial.getExpiraEm().isAfter(now) || credencial.getTentativas() >= MAX_TENTATIVAS
                || credencial.getUsuario() == null || !credencial.getUsuario().getId().equals(usuario.getId())) return false;
        try {
            usuarioService.validarNovaSenha(usuario, novaSenha);
        } catch (IllegalArgumentException invalidPassword) {
            credencial.setTentativas(credencial.getTentativas() + 1);
            return false;
        }
        usuarioService.redefinirSenha(usuario, novaSenha);
        credencial.setConsumidoEm(now);
        return true;
    }

    private String normalizarEmail(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        return new Email(value).valor();
    }

    private String chave(String value) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível processar a recuperação de senha.");
        }
    }

    private void bloquearChaves(String emailKey, String ipKey) {
        long emailLock = chaveBloqueio(emailKey);
        long ipLock = chaveBloqueio(ipKey);
        if (Long.compareUnsigned(emailLock, ipLock) <= 0) {
            recuperacaoRepository.lockRateKey(emailLock);
            if (emailLock != ipLock) recuperacaoRepository.lockRateKey(ipLock);
        } else {
            recuperacaoRepository.lockRateKey(ipLock);
            recuperacaoRepository.lockRateKey(emailLock);
        }
    }

    private long chaveBloqueio(String hashedKey) {
        return Long.parseUnsignedLong(hashedKey.substring(0, 16), 16);
    }

    private String novoToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.");
        }
    }
}
