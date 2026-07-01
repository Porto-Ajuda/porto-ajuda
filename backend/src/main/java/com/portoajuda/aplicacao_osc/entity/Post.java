package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.TipoPost;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_osc", nullable = false)
    private Osc osc;

    @OneToOne(mappedBy = "post", cascade = CascadeType.PERSIST)
    private Vaga vagaVoluntariado;

    @Column(name = "tipo", nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoPost tipo;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "titulo", nullable = false, length = 255)
    private String titulo;

    @Column(name = "imagem_url", length = 500)
    private String imagemUrl;

    @Column(name = "conteudo", columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;
}