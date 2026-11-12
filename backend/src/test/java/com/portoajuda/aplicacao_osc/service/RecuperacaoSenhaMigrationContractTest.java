package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RecuperacaoSenhaMigrationContractTest {
    @Test
    void migrationMatchesRecoveryEntityAndAppliesRlsWithoutIdempotentPartialDdl() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/manual/20261110_password_recovery.sql"));
        assertTrue(sql.contains("CREATE TABLE public.recuperacoes_senha"));
        assertTrue(sql.contains("usuario_id INTEGER NULL REFERENCES public.usuarios(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("token_hash VARCHAR(64) NULL UNIQUE"));
        assertTrue(sql.contains("email_key VARCHAR(64) NOT NULL"));
        assertTrue(sql.contains("ip_key VARCHAR(64) NOT NULL"));
        assertTrue(sql.contains("solicitado_em TIMESTAMPTZ NOT NULL"));
        assertTrue(sql.contains("expira_em TIMESTAMPTZ NOT NULL"));
        assertTrue(sql.contains("consumido_em TIMESTAMPTZ NULL"));
        assertTrue(sql.contains("tentativas INTEGER NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("ALTER TABLE public.recuperacoes_senha ENABLE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("REVOKE ALL ON TABLE public.recuperacoes_senha FROM PUBLIC"));
        assertTrue(sql.startsWith("-- One-time migration"));
        assertTrue(sql.contains("BEGIN;"));
        assertTrue(sql.trim().endsWith("COMMIT;"));
        assertTrue(!sql.contains("IF NOT EXISTS"));
    }
}
