package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AdminAuthCodeSecurityConfigurationTest {

    @Test
    void pepperComPeloMenos32BytesPermiteCriacaoDoBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(AdminAuthCodeSecurity.class)
                .withPropertyValues("admin-auth.code-pepper=" + "P".repeat(32))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(AdminAuthCodeSecurity.class));
                });
    }

    @Test
    void pepperAusenteFalhaNaInicializacaoComDiagnosticoAcionavel() {
        new ApplicationContextRunner()
                .withUserConfiguration(AdminAuthCodeSecurity.class)
                .withPropertyValues("admin-auth.code-pepper=")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    assertTrue(cadeiaDeMensagens(failure).contains("ADMIN_AUTH_CODE_PEPPER"));
                    assertTrue(cadeiaDeMensagens(failure).contains("32 bytes UTF-8"));
                });
    }

    @Test
    void pepperCurtoTambemFalhaNaInicializacao() {
        new ApplicationContextRunner()
                .withUserConfiguration(AdminAuthCodeSecurity.class)
                .withPropertyValues("admin-auth.code-pepper=curto")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    assertTrue(cadeiaDeMensagens(failure).contains("ADMIN_AUTH_CODE_PEPPER"));
                });
    }

    @Test
    void pepperSomenteEspacosFalhaNaInicializacao() {
        new ApplicationContextRunner()
                .withUserConfiguration(AdminAuthCodeSecurity.class)
                .withPropertyValues("admin-auth.code-pepper=" + " ".repeat(40))
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    private String cadeiaDeMensagens(Throwable erro) {
        StringBuilder mensagens = new StringBuilder();
        for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
            if (atual.getMessage() != null) {
                mensagens.append(atual.getMessage()).append('\n');
            }
        }
        return mensagens.toString();
    }
}
