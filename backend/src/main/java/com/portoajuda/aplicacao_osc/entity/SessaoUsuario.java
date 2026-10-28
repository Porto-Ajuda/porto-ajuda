package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sessoes_usuario", indexes = {
        @Index(name = "idx_sessao_usuario", columnList = "usuario_id"),
        @Index(name = "idx_sessao_expiracao", columnList = "expira_em")
})
@Getter
@Setter
@NoArgsConstructor
public class SessaoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // Hash SHA-256 do refresh token; nunca armazenar o token original.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "ultima_atividade_em", nullable = false)
    private Instant ultimaAtividadeEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "lembrar_de_mim", nullable = false)
    private boolean lembrarDeMim;

    @Column(name = "revogada_em")
    private Instant revogadaEm;

    public SessaoUsuario(
            Usuario usuario,
            String tokenHash,
            Instant agora,
            Instant expiraEm,
            boolean lembrarDeMim) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.criadaEm = agora;
        this.ultimaAtividadeEm = agora;
        this.expiraEm = expiraEm;
        this.lembrarDeMim = lembrarDeMim;
    }

    public boolean isRevogada() {
        return revogadaEm != null;
    }
}