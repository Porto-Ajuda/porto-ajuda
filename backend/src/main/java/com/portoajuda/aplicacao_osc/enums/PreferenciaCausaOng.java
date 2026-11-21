package com.portoajuda.aplicacao_osc.enums;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Canonical display groups. Legacy CausaSocial values remain unchanged and map into these groups. */
public enum PreferenciaCausaOng {
    ALIMENTACAO("alimentacao"), CRIANCAS_ADOLESCENTES("criancas-adolescentes"), IDOSOS("idosos"),
    SAUDE("saude"), EDUCACAO("educacao"), ANIMAIS("animais"), MEIO_AMBIENTE("meio-ambiente"),
    MORADIA("moradia"), PESSOAS_COM_DEFICIENCIA("pessoas-com-deficiencia"), MULHERES("mulheres"),
    CULTURA_ESPORTE("cultura-esporte"), EMERGENCIAS("emergencias"),
    ASSISTENCIA_SOCIAL("assistencia-social"), DIREITOS_HUMANOS_CIDADANIA("direitos-humanos-cidadania");

    private final String id;
    PreferenciaCausaOng(String id) { this.id = id; }
    public String id() { return id; }

    public static Set<String> ids() {
        return java.util.Arrays.stream(values()).map(PreferenciaCausaOng::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static Set<String> fromCauses(Collection<CausaSocial> causes) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (causes == null) return result;
        for (CausaSocial cause : causes) {
            if (cause == null) continue;
            switch (cause) {
                case COMBATE_FOME -> result.add(ALIMENTACAO.id);
                case CRIANCAS_ADOLESCENTES -> result.add(CRIANCAS_ADOLESCENTES.id);
                case IDOSOS -> result.add(IDOSOS.id);
                case SAUDE -> result.add(SAUDE.id);
                case EDUCACAO -> result.add(EDUCACAO.id);
                case ANIMAIS -> result.add(ANIMAIS.id);
                case MEIO_AMBIENTE -> result.add(MEIO_AMBIENTE.id);
                case MORADIA -> result.add(MORADIA.id);
                case PESSOAS_COM_DEFICIENCIA -> result.add(PESSOAS_COM_DEFICIENCIA.id);
                case APOIO_MULHERES -> result.add(MULHERES.id);
                case CULTURA, ESPORTE, CULTURA_ESPORTE -> result.add(CULTURA_ESPORTE.id);
                case AJUDA_HUMANITARIA_EMERGENCIAS -> result.add(EMERGENCIAS.id);
                case INCLUSAO_SOCIAL -> {
                    result.add(ASSISTENCIA_SOCIAL.id);
                    result.add(DIREITOS_HUMANOS_CIDADANIA.id);
                }
                case COMBATE_POBREZA, ASSISTENCIA_SOCIAL -> result.add(ASSISTENCIA_SOCIAL.id);
                case DIREITOS_HUMANOS_CIDADANIA -> result.add(DIREITOS_HUMANOS_CIDADANIA.id);
            }
        }
        return result;
    }
}
