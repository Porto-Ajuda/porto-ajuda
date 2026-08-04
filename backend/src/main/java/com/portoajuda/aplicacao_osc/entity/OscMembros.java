package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "osc_membros")
public class OscMembros {
    @Setter(AccessLevel.NONE)
    @EmbeddedId
    private OscMembroId id;

    @MapsId("oscId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_osc", nullable = false)
    private Osc osc;

    @MapsId("usuarioId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "cargo_osc", length = 100)
    private String cargoOsc;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;
}