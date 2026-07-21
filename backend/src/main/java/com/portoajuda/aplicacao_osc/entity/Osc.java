package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.enums.TipoChavePix;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.Email;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "oscs")
public class Osc {

    @Setter(AccessLevel.NONE)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "cnpj", nullable = false, length = 14, unique = true)
    private Cnpj cnpj;

    @ElementCollection(targetClass = CausaSocial.class)
    @CollectionTable(
            name = "osc_causas",
            joinColumns = @JoinColumn(name = "id_osc")
    )
    @Column(name = "causa")
    @Enumerated(EnumType.STRING)
    private Set<CausaSocial> causas = new HashSet<>();

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

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;
}