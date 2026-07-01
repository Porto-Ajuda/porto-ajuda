package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "candidaturas_voluntariado")
public class CandidaturasVoluntariado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vaga", nullable = false)
    private Vaga vaga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "mensagem", columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusCandidatura status;

    @Column(name = "data_candidatura", nullable = false)
    private LocalDate dataCandidatura;
}
