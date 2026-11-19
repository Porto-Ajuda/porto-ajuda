package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RequestFinalizarVoluntariadoDTO(
        @NotNull LocalDate dataInicioReal,
        @NotNull LocalDate dataFimReal,
        @NotNull @Valid List<Participante> participantes) {
    public record Participante(
            @NotNull @Positive Integer candidaturaId,
            @NotNull Boolean concluiu,
            @DecimalMin(value = "0.01") @DecimalMax(value = "999999.99") BigDecimal horasRealizadas) {}
}
