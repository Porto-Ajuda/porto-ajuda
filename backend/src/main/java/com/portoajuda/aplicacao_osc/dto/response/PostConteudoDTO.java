package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import java.time.LocalDateTime;

/** Conteúdo da ONG. Informações de autoria e candidaturas não são expostas na resposta. */
public record PostConteudoDTO(
        Integer id,
        Integer oscId,
        String nomeOsc,
        CausaSocial causaPrincipalOsc,
        String cidadeOsc,
        String estadoOsc,
        Double latitudeOsc,
        Double longitudeOsc,
        TipoPost tipo,
        boolean ativo,
        String titulo,
        String conteudo,
        String imagemUrl,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao,
        VagaConteudoDTO vaga,
        boolean podeEditar) {

    public static PostConteudoDTO from(Post post, VagaConteudoDTO vaga, boolean podeEditar) {
        return new PostConteudoDTO(
                post.getId(),
                post.getOsc() == null ? null : post.getOsc().getId(),
                post.getOsc() == null ? null : post.getOsc().getNome(),
                post.getOsc() == null ? null : post.getOsc().getCausaPrincipal(),
                post.getOsc() == null ? null : post.getOsc().getCidade(),
                post.getOsc() == null ? null : post.getOsc().getEstado(),
                post.getOsc() == null ? null : post.getOsc().getLatitude(),
                post.getOsc() == null ? null : post.getOsc().getLongitude(),
                post.getTipo(),
                post.isAtivo(),
                post.getTitulo(),
                post.getConteudo(),
                post.getImagemUrl(),
                post.getDataCriacao(),
                post.getDataAtualizacao(),
                vaga,
                podeEditar);
    }
}
