package com.portoajuda.aplicacao_osc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "post_causas")
public class PostCausas {

    @EmbeddedId
    private PostCausaId id;

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_post")
    private Post post;

    public PostCausas() {}
}