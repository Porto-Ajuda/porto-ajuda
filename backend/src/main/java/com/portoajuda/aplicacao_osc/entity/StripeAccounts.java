
package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.StatusStripe;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "stripe_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_oscs_stripe_osc",
                        columnNames = "id_osc"
                )
        }
)
public class StripeAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_osc",
            nullable = false,
            unique = true
    )
    private Osc osc;

    @Column(name = "stripe_account_id", length = 255)
    private String stripeAccountId;

    @Column(name = "livemode")
    private boolean livemode;

    @CreationTimestamp
    @Column(
            name = "data_criacao",
            nullable = false,
            updatable = false
    )
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;
}
