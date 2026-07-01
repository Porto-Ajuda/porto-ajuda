package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class UsuarioCargoId implements Serializable {
    @Column(name = "id_usuario", nullable = false)
    private Integer usuarioId;

    @Column(name = "id_cargo", nullable = false)
    private Integer cargoId;
}
