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
public class PostCausaId implements Serializable {
        @Column(name = "id_post", nullable = false)
        private Integer postId;

        @Enumerated(EnumType.STRING)
        @Column(name = "causa", nullable = false)
        private CausaSocial causa;
}