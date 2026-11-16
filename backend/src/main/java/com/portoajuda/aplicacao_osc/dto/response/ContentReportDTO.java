package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;

public record ContentReportDTO(String status, String mensagem, Instant registradoEm) { }
