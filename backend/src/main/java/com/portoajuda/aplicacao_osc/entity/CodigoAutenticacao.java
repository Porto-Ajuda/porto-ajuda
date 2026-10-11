
package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "codigos_autenticacao",
    indexes = {
        @Index(
            name = "idx_codigo_autenticacao_usuario",
            columnList = "usuario_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CodigoAutenticacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "codigo_hash",
        nullable = false,
        unique = true,
        length = 64
    )
    private String codigoHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "utilizado", nullable = false)
    private boolean utilizado = false;

    public CodigoAutenticacao(
            String codigoHash,
            Usuario usuario,
            Instant expiraEm
    ) {
        this.codigoHash = codigoHash;
        this.usuario = usuario;
        this.expiraEm = expiraEm;
        this.utilizado = false;
    }
}
