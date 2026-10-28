package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.ModalidadeVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import jakarta.persistence.*;
import lombok.Setter;

import java.time.LocalDate;
@Setter
@Entity
@Table(name = "vagas_voluntariado")
public class Vaga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "id_post", nullable = false)
    private Post post;

    @Column(name = "quantidade_vagas")
    private Integer quantidadeVagas;

    @Column(name = "modalidade", nullable = false)
    @Enumerated(EnumType.STRING)
    private ModalidadeVoluntariado modalidade;

    @Column(name = "requisitos", columnDefinition = "TEXT")
    private String requisitos;

    @Column(name = "carga_horaria", nullable = false, length = 100)
    private String cargaHoraria;

    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;

    @Column(name = "bairro", nullable = false, length = 100)
    private String bairro;

    @Column(name = "rua", length = 100)
    private String rua;

    @Column(name = "numero", length = 100)
    private String numero;

    @Column(name = "complemento", length = 100)
    private String complemento;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusVaga status;
}