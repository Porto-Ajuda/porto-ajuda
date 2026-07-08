package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.Genero;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Setter(AccessLevel.NONE)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private Set<Post> posts;

    @Column(name = "cpf", nullable = false, length = 11, unique = true)
    private Cpf cpf;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "nome_social", length = 255)
    private String nomeSocial;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "genero", nullable = false)
    @Enumerated(EnumType.STRING)
    private Genero genero;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private Email email;

    @Column(name = "telefone", length = 15)
    private String telefone;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senha;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;
}
