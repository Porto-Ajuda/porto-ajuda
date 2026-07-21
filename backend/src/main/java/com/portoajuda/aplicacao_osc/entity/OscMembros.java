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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "osc_membro_permissao",
            joinColumns = {
                @JoinColumn(name = "id_osc", nullable = false),
                @JoinColumn(name = "id_usuario", nullable = false)
            },
            inverseJoinColumns = @JoinColumn(name = "id_permissao", nullable = false))
    private Set<Permissao> permissoes;

    @Column(name = "cargo_osc", length = 100)
    private String cargoOsc;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;
}