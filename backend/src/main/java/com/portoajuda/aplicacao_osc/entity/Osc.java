package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.TipoChavePix;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "oscs")
public class Osc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "cnpj", nullable = false, length = 14, unique = true)
    private Cnpj cnpj;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_criador", nullable = false)
    private Usuario usuario;

    @OneToMany(mappedBy = "osc", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private Set<Post> posts;

    @OneToMany(mappedBy = "osc", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private Set<OscMembros> oscMembros;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "chave_pix", nullable = false, length = 255)
    private String chavePix;

    @Column(name = "tipo_chave_pix", nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoChavePix tipoChavePix;

    @Column(name = "descricao", columnDefinition = "TEXT", nullable = false)
    private String descricao;

    @Column(name = "email", nullable = false, length = 255)
    private Email email;

    @Column(name = "telefone", length = 15)
    private String telefone;

    @Column(name = "site", length = 255)
    private String site;

    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;

    @Column(name = "cep", nullable = false, length = 9)
    private String cep;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;
}