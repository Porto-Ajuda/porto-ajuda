package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "osc_causas")
public class OscCausas {
    @EmbeddedId
    private OscCausasId id;

    @MapsId("oscId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_osc", nullable = false)
    private Osc osc;
}