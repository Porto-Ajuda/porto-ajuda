
package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.PreferenciasAcessibilidade;
import java.util.Set;

public record ResponsePreferenciasAcessibilidadeDTO(
    Integer tamanhoFonte,
    String tema,
    boolean altoContraste,
    boolean libras,
    Set<String> tiposOng,
    boolean causasPersistidas
) {
    public ResponsePreferenciasAcessibilidadeDTO(Integer tamanhoFonte, String tema, boolean altoContraste, boolean libras) {
        this(tamanhoFonte, tema, altoContraste, libras, Set.of(), false);
    }

    public static ResponsePreferenciasAcessibilidadeDTO de(
            PreferenciasAcessibilidade preferencias, Set<String> tiposOng, boolean causasPersistidas) {

        return new ResponsePreferenciasAcessibilidadeDTO(
            preferencias.getTamanhoFonte(),
            preferencias.getTema(),
            preferencias.isAltoContraste(),
            preferencias.isLibras(),
            tiposOng == null ? Set.of() : Set.copyOf(tiposOng),
            causasPersistidas
        );
    }
}
