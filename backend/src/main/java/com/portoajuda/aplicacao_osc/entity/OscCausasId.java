package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.io.Serializable;

@EqualsAndHashCode
@Setter
@Getter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class OscCausasId implements Serializable {
    @Column(name = "id_osc", nullable = false)
    private Integer oscId;

    @Column(name = "causa", nullable = false)
    @Enumerated(EnumType.STRING)
    private CausaSocial causa;

}
