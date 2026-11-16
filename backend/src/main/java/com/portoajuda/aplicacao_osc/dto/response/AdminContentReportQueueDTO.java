package com.portoajuda.aplicacao_osc.dto.response;

import java.util.List;

public record AdminContentReportQueueDTO(int limite, int deslocamento, long totalItens,
                                         List<AdminContentReportQueueItemDTO> itens) { }
