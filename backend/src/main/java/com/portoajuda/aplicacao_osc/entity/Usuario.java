package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private Set<Post> posts;

    @Column(name = "cpf", nullable = false, length = 11, unique = true)
    private Cpf cpf;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "email", nullable = false, length = 255)
    private Email email;

    @Column(name = "telefone", length = 15)
    private String telefone;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String password;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;
}
