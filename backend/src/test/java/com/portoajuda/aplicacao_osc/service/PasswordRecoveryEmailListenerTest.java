package com.portoajuda.aplicacao_osc.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PasswordRecoveryEmailListenerTest {
    @Test
    void emailLinkComesOnlyFromValidatedConfiguredFrontendOrigin() {
        EmailService email = mock(EmailService.class);
        PasswordRecoveryEmailListener listener = new PasswordRecoveryEmailListener(
                email, "https://frontend.example.test/");
        String syntheticToken = "synthetic-token-never-log-this";

        listener.enviarAposCommit(new PasswordRecoveryRequested("person@example.invalid", syntheticToken));

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(email).enviarRecuperacaoSenha(org.mockito.ArgumentMatchers.eq("person@example.invalid"), link.capture());
        assertTrue(link.getValue().startsWith("https://frontend.example.test/login#redefinir-senha/"));
        assertTrue(link.getValue().endsWith(syntheticToken));
    }

    @Test
    void untrustedOrHostDerivedUrlFormsFailAtStartup() {
        EmailService email = mock(EmailService.class);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new PasswordRecoveryEmailListener(email, "//attacker.example"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new PasswordRecoveryEmailListener(email, "https://trusted.example?host=evil"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new PasswordRecoveryEmailListener(email, "http://public.example"));
    }

    @Test
    void providerFailureIsContainedWithoutLoggingOrReturningTheCredential() {
        EmailService email = mock(EmailService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic provider failure"))
                .when(email).enviarRecuperacaoSenha(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
        PasswordRecoveryEmailListener listener = new PasswordRecoveryEmailListener(
                email, "http://localhost:5500");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> listener.enviarAposCommit(
                new PasswordRecoveryRequested("person@example.invalid", "synthetic-token")));
    }
}
