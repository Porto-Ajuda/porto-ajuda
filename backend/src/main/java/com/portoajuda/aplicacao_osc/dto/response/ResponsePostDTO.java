package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.enums.TipoPost;

import java.time.LocalDateTime;

public record ResponsePostDTO(
        Integer id,
        String nomeOsc,
        String titulo,
        String conteudo,
        String imagemUrl,
        TipoPost tipo,
        boolean ativo,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao
) {
    public static ResponsePostDTO from(Post post) {
        return new ResponsePostDTO(
                post.getId(),
                post.getOsc().getNome(),
                post.getTitulo(),
                post.getConteudo(),
                post.getImagemUrl(),
                post.getTipo(),
                post.isAtivo(),
                post.getDataCriacao(),
                post.getDataAtualizacao()
        );
    }
}