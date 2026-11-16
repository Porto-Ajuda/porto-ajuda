package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;
import java.util.List;

public record AdminContentReportDetailDTO(
        String tipo, Integer alvoId, String titulo, String conteudo,
        boolean disponivel, boolean protecaoAutomaticaAtiva,
        List<Denuncia> denuncias, List<Revisao> historicoRevisao, List<Bloqueio> restricoesAtivas) {
    public record Denuncia(long id, String motivo, String justificativa, String status, Instant registradaEm) { }
    public record Revisao(String decisao, String justificativa, String perfil, Instant ocorreuEm) { }
    public record Bloqueio(long id, String origem, String tipoConteudo, String justificativa, Instant criadoEm) { }
}
