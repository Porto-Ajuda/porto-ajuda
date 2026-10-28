
package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.PreferenciasAcessibilidade;

public record ResponsePreferenciasAcessibilidadeDTO(
    Integer tamanhoFonte,
    String tema,
    boolean altoContraste,
    boolean libras
) {
    public static ResponsePreferenciasAcessibilidadeDTO de(
            PreferenciasAcessibilidade preferencias) {

        return new ResponsePreferenciasAcessibilidadeDTO(
            preferencias.getTamanhoFonte(),
            preferencias.getTema(),
            preferencias.isAltoContraste(),
            preferencias.isLibras()
        );
    }
}
