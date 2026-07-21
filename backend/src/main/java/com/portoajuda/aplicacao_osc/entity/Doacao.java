package com.portoajuda.aplicacao_osc.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.portoajuda.aplicacao_osc.enums.StatusDoacao;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "doacoes")
public class Doacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "id_post")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "id_osc", nullable = false)
    private Osc osc;

    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "mensagem", columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusDoacao status;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @CreationTimestamp
    @Column(name = "data_pag", nullable = false, updatable = false)
    private LocalDateTime dataPag;

    @Column(name = "txid", nullable = false, length = 64, unique = true)
    private String txid;

    @Column(name = "end2end_id", nullable = false, length = 40, unique = true)
    private String endToEndId;

    @Column(name = "pix_copia_cola", nullable = false, columnDefinition = "TEXT")
    private String pixCopiaCola;

    @Column(name = "qrcode", columnDefinition = "TEXT")
    private String qrcode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_webhook", columnDefinition = "jsonb")
    private JsonNode payload;
}