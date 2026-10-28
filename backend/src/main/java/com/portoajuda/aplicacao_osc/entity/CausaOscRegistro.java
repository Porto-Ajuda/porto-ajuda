package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/** Uma linha de osc_causas. causa_principal é repetida nas linhas da mesma OSC. */
@Embeddable
public class CausaOscRegistro {

    @Enumerated(EnumType.STRING)
    @Column(name = "causa", length = 50)
    private CausaSocial causa;

    @Enumerated(EnumType.STRING)
    @Column(name = "causa_principal", length = 50)
    private CausaSocial causaPrincipal;

    protected CausaOscRegistro() {
    }

    public CausaOscRegistro(CausaSocial causa, CausaSocial causaPrincipal) {
        this.causa = causa;
        this.causaPrincipal = causaPrincipal;
    }

    public CausaSocial getCausa() {
        return causa;
    }

    public CausaSocial getCausaPrincipal() {
        return causaPrincipal;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) return true;
        if (!(outro instanceof CausaOscRegistro that)) return false;
        return causa == that.causa && causaPrincipal == that.causaPrincipal;
    }

    @Override
    public int hashCode() {
        return Objects.hash(causa, causaPrincipal);
    }
}
