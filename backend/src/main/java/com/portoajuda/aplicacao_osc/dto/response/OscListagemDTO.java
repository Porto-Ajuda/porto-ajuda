package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;

import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashSet;

/** Dados públicos necessários para a lista e o mapa de proximidade. */
public record OscListagemDTO(
        Integer id,
        String nome,
        String logo,
        CausaSocial causaPrincipal,
        Set<CausaSocial> causasSecundarias,
        /** Compatibilidade para consumidores antigos da lista plana. */
        Set<CausaSocial> causas,
        Set<Integer> ods,
        boolean ativo,
        String descricaoCurta,
        String descricao,
        String rua,
        String numero,
        String bairro,
        String cidade,
        String estado,
        String pais,
        double latitude,
        double longitude
) {
    public static OscListagemDTO de(Osc osc) {
        return new OscListagemDTO(
                osc.getId(), osc.getNome(), osc.getLogo(), osc.getCausaPrincipal(),
                copiar(osc.getCausasSecundarias()), copiar(osc.getCausas()), copiar(osc.getOds()),
                osc.isAtivo(), osc.getDescricaoCurta(), osc.getDescricao(), osc.getRua(),
                osc.getNumero(), osc.getBairro(), osc.getCidade(), osc.getEstado(), osc.getPais(),
                osc.getLatitude(), osc.getLongitude());
    }

    private static <T> Set<T> copiar(Set<T> valores) {
        return valores == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(valores));
    }
}
