package com.portoajuda.aplicacao_osc.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacaoAbusoUsuarioServiceTest {
    @Mock AdminAuthControlsService controls;
    @Mock PasswordEncoder passwordEncoder;
    AutenticacaoAbusoUsuarioService service;

    @BeforeEach
    void setup() {
        when(passwordEncoder.encode(any())).thenReturn("synthetic-dummy-hash");
        service = new AutenticacaoAbusoUsuarioService(controls, passwordEncoder);
    }

    @Test
    void loginReservesOnlyAnIpBucketAndUsesAWindowedLock() {
        when(controls.isLocked(eq("START_IP"), eq("user-login-ip:198.51.100.12"), any())).thenReturn(false, false);
        assertTrue(service.registrarTentativaLoginPorIp("198.51.100.12"));
        verify(controls).increment(eq("START_IP"), eq("user-login-ip:198.51.100.12"), any(),
                eq(Duration.ofMinutes(15)), eq(AutenticacaoAbusoUsuarioService.LOGIN_IP_ATTEMPTS + 1),
                eq(Duration.ofMinutes(15)), eq(true));
        verify(controls, never()).increment(eq("ACCOUNT_FAILURE"), any(), any(), any(),
                anyInt(), any(), anyBoolean());
    }

    @Test
    void excessiveLoginIpAttemptsAreDeniedAfterAtomicCounterIncrement() {
        when(controls.isLocked(eq("START_IP"), eq("user-login-ip:198.51.100.13"), any())).thenReturn(false, true);
        assertFalse(service.registrarTentativaLoginPorIp("198.51.100.13"));
    }

    @Test
    void passwordlessCodeExchangeUsesItsOwnNamespacedIpBucket() {
        when(controls.isLocked(eq("START_IP"), eq("user-login-code-ip:198.51.100.17"), any()))
                .thenReturn(false, false);
        assertTrue(service.registrarTentativaCodigoLoginPorIp("198.51.100.17"));
        verify(controls).increment(eq("START_IP"), eq("user-login-code-ip:198.51.100.17"), any(),
                eq(Duration.ofMinutes(15)), eq(AutenticacaoAbusoUsuarioService.LOGIN_IP_ATTEMPTS + 1),
                eq(Duration.ofMinutes(15)), eq(true));
    }

    @Test
    void accountFailuresHaveAFiniteWindowAndExpiredSuccessfulLoginCanResetThem() {
        service.registrarFalhaLogin("user@example.invalid");
        verify(controls).increment(eq("ACCOUNT_FAILURE"), eq("user-login-failure:user@example.invalid"), any(),
                eq(Duration.ofMinutes(15)), eq(AutenticacaoAbusoUsuarioService.LOGIN_ACCOUNT_FAILURES),
                eq(Duration.ofMinutes(15)), eq(true));
        service.limparFalhasLogin("user@example.invalid");
        verify(controls).reset(eq("ACCOUNT_FAILURE"), eq("user-login-failure:user@example.invalid"), any(Instant.class));
    }

    @Test
    void registrationAndConfirmationResendUseBoundedIpAndPendingAccountBuckets() {
        when(controls.isLocked(any(), any(), any())).thenReturn(false);
        service.permitirCadastro("198.51.100.14");
        service.permitirReenvioConfirmacaoIp("198.51.100.14");
        service.permitirReenvioConfirmacaoConta("pending@example.invalid");

        verify(controls).increment(eq("START_IP"), eq("user-registration-ip:198.51.100.14"), any(),
                eq(Duration.ofHours(1)), eq(AutenticacaoAbusoUsuarioService.REGISTRATION_IP_ATTEMPTS + 1),
                eq(Duration.ofHours(1)), eq(true));
        verify(controls).increment(eq("START_IP"), eq("user-resend-ip:198.51.100.14"), any(),
                eq(Duration.ofHours(1)), eq(AutenticacaoAbusoUsuarioService.RESEND_WINDOW_IP + 1),
                eq(Duration.ofHours(1)), eq(true));
        verify(controls).increment(eq("START_ACCOUNT"), eq("user-resend-account:pending@example.invalid"), any(),
                eq(Duration.ofHours(1)), eq(AutenticacaoAbusoUsuarioService.RESEND_WINDOW_ACCOUNT + 1),
                eq(Duration.ofHours(1)), eq(true));
    }

    @Test
    void unknownAccountPasswordCheckUsesDummyHashAndNeverMatches() {
        when(passwordEncoder.matches("synthetic-password", "synthetic-dummy-hash")).thenReturn(true);
        assertFalse(service.verificarSenhaMesmoParaContaInexistente(null, "synthetic-password"));
        verify(passwordEncoder).matches("synthetic-password", "synthetic-dummy-hash");
    }
}
