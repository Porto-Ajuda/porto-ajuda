package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;

public record SituacaoMinhaCandidaturaDTO(boolean jaSeCandidatou, StatusCandidatura status) {
}
