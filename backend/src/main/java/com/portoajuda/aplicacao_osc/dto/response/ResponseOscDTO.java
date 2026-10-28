package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;

import java.util.Set;

public record ResponseOscDTO(
        Integer id,
        String cnpj,
        String nome,
        CausaSocial causaPrincipal,
        Set<CausaSocial> causasSecundarias,
        /** Compatibilidade para consumidores antigos da lista plana. */
        Set<CausaSocial> causas,
        Set<Integer> ods
) {
}
