package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reuses the existing HMAC-keyed admin_auth_controls table for ordinary account abuse controls. */
@Service
public class AutenticacaoAbusoUsuarioService {
    static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    static final int LOGIN_IP_ATTEMPTS = 60;
    static final int LOGIN_ACCOUNT_FAILURES = 5;
    static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);
    static final int REGISTRATION_IP_ATTEMPTS = 10;
    static final int RESEND_WINDOW_IP = 10;
    static final int RESEND_WINDOW_ACCOUNT = 3;
    static final int REACTIVATION_WINDOW_ACCOUNT = 10;
    static final Duration REPORT_WINDOW = Duration.ofHours(1);
    static final int REPORT_ACCOUNT_ATTEMPTS = 5;
    static final int REPORT_IP_ATTEMPTS = 30;

    private final AdminAuthControlsService controls;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AutenticacaoAbusoUsuarioService(
            AdminAuthControlsService controls,
            PasswordEncoder passwordEncoder) {
        this.controls = controls;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarTentativaLoginPorIp(String enderecoRemoto) {
        Instant now = Instant.now();
        return reservar("START_IP", "user-login-ip", enderecoRemoto,
                now, LOGIN_WINDOW, LOGIN_IP_ATTEMPTS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarTentativaCodigoLoginPorIp(String enderecoRemoto) {
        return reservar("START_IP", "user-login-code-ip", enderecoRemoto,
                Instant.now(), LOGIN_WINDOW, LOGIN_IP_ATTEMPTS);
    }

    @Transactional(readOnly = true)
    public boolean contaLoginBloqueada(String emailNormalizado) {
        return controls.isLocked("ACCOUNT_FAILURE", subject("user-login-failure", emailNormalizado),
                Instant.now());
    }

    public boolean verificarSenhaMesmoParaContaInexistente(Usuario usuario, String senhaInformada) {
        String hash = usuario == null || usuario.getSenha() == null
                ? dummyPasswordHash
                : usuario.getSenha();
        boolean corresponde = senhaInformada != null && passwordEncoder.matches(senhaInformada, hash);
        return usuario != null && corresponde;
    }

    @Transactional
    public void registrarFalhaLogin(String emailNormalizado) {
        Instant now = Instant.now();
        controls.increment("ACCOUNT_FAILURE", subject("user-login-failure", emailNormalizado), now, LOGIN_WINDOW,
                LOGIN_ACCOUNT_FAILURES, LOGIN_WINDOW, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void limparFalhasLogin(String emailNormalizado) {
        controls.reset("ACCOUNT_FAILURE", subject("user-login-failure", emailNormalizado), Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean permitirCadastro(String enderecoRemoto) {
        return reservar("START_IP", "user-registration-ip", enderecoRemoto,
                Instant.now(), REGISTRATION_WINDOW, REGISTRATION_IP_ATTEMPTS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean permitirReenvioConfirmacaoIp(String enderecoRemoto) {
        return reservar("START_IP", "user-resend-ip", enderecoRemoto,
                Instant.now(), REGISTRATION_WINDOW, RESEND_WINDOW_IP);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean permitirReenvioConfirmacaoConta(String emailNormalizado) {
        return reservar("START_ACCOUNT", "user-resend-account", emailNormalizado,
                Instant.now(), REGISTRATION_WINDOW, RESEND_WINDOW_ACCOUNT);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean permitirReativacaoConta(String emailNormalizado) {
        return reservar("START_ACCOUNT", "user-reactivation-account", emailNormalizado,
                Instant.now(), REGISTRATION_WINDOW, REACTIVATION_WINDOW_ACCOUNT);
    }

    /** Limites antiflood sem bloquear a conta: cinco denúncias/hora por usuário e trinta/IP. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean permitirDenuncia(Integer usuarioId, String enderecoRemoto) {
        Instant now = Instant.now();
        boolean conta = reservar("START_ACCOUNT", "content-report-user", String.valueOf(usuarioId),
                now, REPORT_WINDOW, REPORT_ACCOUNT_ATTEMPTS);
        boolean ip = reservar("START_IP", "content-report-ip", enderecoRemoto,
                now, REPORT_WINDOW, REPORT_IP_ATTEMPTS);
        return conta && ip;
    }

    private boolean reservar(String tipo, String namespace, String identificador,
            Instant now, Duration janela, int maximo) {
        String subject = subject(namespace, identificador);
        if (controls.isLocked(tipo, subject, now)) return false;
        controls.increment(tipo, subject, now, janela, maximo + 1, janela, true);
        return !controls.isLocked(tipo, subject, now);
    }

    static String subject(String namespace, String identifier) {
        return namespace + ":" + (identifier == null ? "unknown" : identifier.trim());
    }
}
