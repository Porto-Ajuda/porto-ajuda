package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;

public record AdminContentReportQueueItemDTO(
        String tipo, Integer alvoId, String titulo, String nomeResponsavel,
        long pendentes, long qualificadasNaJanela, boolean protecaoAutomaticaAtiva,
        boolean restricaoAdministrativaAtiva, Instant denunciaMaisAntiga) { }
