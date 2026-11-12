
package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "osc_membros")
public class OscMembros {

    @Setter(AccessLevel.NONE)
    @EmbeddedId
    private OscMembroId id;

    @MapsId("oscId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_osc", nullable = false)
    private Osc osc;

    @MapsId("usuarioId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "cargo_osc", length = 100, nullable = false)
    private CargoOsc cargoOsc;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    public void inicializarId() {
        if (osc == null || osc.getId() == null
                || usuario == null || usuario.getId() == null) {
            throw new IllegalStateException(
                    "OSC e usuário devem estar identificados antes de criar o vínculo."
            );
        }

        this.id = new OscMembroId(osc.getId(), usuario.getId());
    }
}
