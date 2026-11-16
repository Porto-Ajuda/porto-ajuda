package com.portoajuda.aplicacao_osc.service;

/**
 * Future trusted-source port for authorization checks independent of uploaded files and model output.
 * No implementation exists in phase 1; an AI finding must never satisfy this contract.
 */
@FunctionalInterface
public interface VerificacaoAutoridadeRepresentanteIndependente {
    Resultado verificar(int oscId, String representativeReference);

    enum Resultado { CONFIRMADA_EM_FONTE_INDEPENDENTE, NAO_CONFIRMADA, INDISPONIVEL }
}
