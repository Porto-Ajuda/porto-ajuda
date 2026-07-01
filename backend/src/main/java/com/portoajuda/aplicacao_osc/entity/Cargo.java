package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.*;

import java.util.Set;

@Entity
@Table(name = "cargos")
public class Cargo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "cargo_permissoes",
            joinColumns = @JoinColumn(
                    name = "id_cargo", nullable = false),
            inverseJoinColumns = @JoinColumn(
                    name = "id_permissao", nullable = false))
    private Set<Permissao> permissoes;
}
