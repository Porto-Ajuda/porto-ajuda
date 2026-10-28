
package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "preferencias_acessibilidade")
public class PreferenciasAcessibilidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_usuario",
        nullable = false,
        unique = true
    )
    private Usuario usuario;

    @Column(name = "tamanho_fonte", nullable = false)
    private Integer tamanhoFonte = 100;

    @Column(name = "tema", nullable = false, length = 10)
    private String tema = "escuro";

    @Column(name = "alto_contraste", nullable = false)
    private boolean altoContraste = false;

    @Column(name = "libras", nullable = false)
    private boolean libras = false;
}
