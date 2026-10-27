package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.Email;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;
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

        @BatchSize(size = 100)
        @ElementCollection(targetClass = CausaSocial.class)
        @CollectionTable(name = "osc_causas", joinColumns = @JoinColumn(name = "id_osc"))
        @Column(name = "causa")
        @Enumerated(EnumType.STRING)
        private Set<CausaSocial> causas = new HashSet<>();

        @BatchSize(size = 100)
        @ElementCollection
        @CollectionTable(name = "osc_ods", joinColumns = @JoinColumn(name = "id_osc"))
        @Column(name = "ods", nullable = false)
        private Set<Integer> ods = new HashSet<>();

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "id_usuario_criador", nullable = false)
        private Usuario usuario;

        @OneToMany(mappedBy = "osc", cascade = CascadeType.PERSIST, orphanRemoval = true)
        private Set<Post> posts;

        @OneToMany(mappedBy = "osc", cascade = CascadeType.PERSIST, orphanRemoval = true)
        private Set<OscMembros> oscMembros;

        @Column(name = "nome", nullable = false, length = 255)
        private String nome;

        @Column(name = "descricao_curta", length = 200)
        private String descricaoCurta;

        @Column(name = "ano_fundacao")
        private Integer anoFundacao;

        @Column(name = "logo", length = 1000)
        private String logo;

        @Column(name = "ativo", nullable = false)
        private boolean ativo;

        @Column(name = "descricao", columnDefinition = "TEXT", nullable = false)
        private String descricao;

        @Column(name = "email", nullable = false, length = 255)
        private Email email;

        @Column(name = "telefone", length = 15)
        private String telefone;

        @Column(name = "whatsapp", length = 20)
        private String whatsapp;

        @Column(name = "instagram", length = 500)
        private String instagram;

        @Column(name = "site", length = 255)
        private String site;

        @Column(name = "linkedin", length = 500)
        private String linkedin;

        @Column(name = "youtube", length = 500)
        private String youtube;

        @Column(name = "area_geografica", length = 30)
        private String areaGeografica;

        @Column(name = "cidade", nullable = false, length = 100)
        private String cidade;

        @Column(name = "estado", length = 100)
        private String estado;

        @Column(name = "pais", length = 100)
        private String pais;

        @Column(name = "cep", nullable = false, length = 9)
        private String cep;

        @Column(name = "bairro", nullable = false, length = 100)
        private String bairro;

        @Column(name = "rua", nullable = false, length = 100)
        private String rua;

        @Column(name = "numero", length = 100)
        private String numero;

        @Column(name = "complemento", length = 100)
        private String complemento;

        @Column(name = "latitude", length = 100)
        private double latitude;

        @Column(name = "longitude", length = 100)
        private double longitude;

        @Column(name = "dias_funcionamento", length = 100)
        private String diasHorariosFuncionamento;

        @Column(name = "horario_abertura")
        private LocalTime horarioAbertura;

        @Column(name = "horario_fechamento")
        private LocalTime horarioFechamento;

        @Column(name = "atendimento_presencial")
        private Boolean atendimentoPresencial;

        @Column(name = "atendimento_online")
        private Boolean atendimentoOnline;

        @Column(name = "necessita_agendamento")
        private Boolean necessitaAgendamento;

        @Column(name = "aceita_voluntarios")
        private Boolean aceitaVoluntarios;

        @Column(name = "frequencia_voluntariado", length = 30)
        private String frequenciaVoluntariado;

        @Column(name = "idade_minima_voluntario")
        private Integer idadeMinimaVoluntario;

        @Column(name = "processo_seletivo_voluntario")
        private Boolean processoSeletivoVoluntario;

        @Column(name = "emite_certificado_voluntario")
        private Boolean emiteCertificadoVoluntario;

        @Column(name = "atividades_voluntariado", columnDefinition = "TEXT")
        private String atividadesVoluntariado;

        @CreationTimestamp
        @Column(name = "data_criacao", nullable = false, updatable = false)
        private LocalDateTime dataCriacao;

        @UpdateTimestamp
        @Column(name = "data_atualizacao", nullable = false)
        private LocalDateTime dataAtualizacao;
}
