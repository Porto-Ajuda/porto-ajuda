package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Integer> {
    @Modifying
    @Query("UPDATE Post p SET p.usuario = null WHERE p.usuario.id = :usuarioId")
    int desvincularAutorPorUsuarioId(@Param("usuarioId") Integer usuarioId);

    @Modifying
    @Query("DELETE FROM Post p WHERE p.osc.id = :oscId")
    int excluirPorOscId(@Param("oscId") Integer oscId);

    Optional<Post> findById(Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p JOIN FETCH p.osc o WHERE p.id = :postId")
    Optional<Post> findForUpdateById(@Param("postId") Integer postId);

    boolean existsByImagemUrl(String imagemUrl);

    @Query("select p.imagemUrl from Post p where p.imagemUrl is not null")
    List<String> listarUrlsDeImagens();

    boolean existsByImagemUrlAndIdNot(String imagemUrl, Integer id);

    List<Post> findByTipo(TipoPost tipo);

    List<Post> findByOscId(Integer oscId);

    @EntityGraph(attributePaths = {"osc", "vagaVoluntariado"})
    List<Post> findByOsc_IdOrderByDataCriacaoDesc(Integer oscId);

    @EntityGraph(attributePaths = {"osc", "vagaVoluntariado"})
    @Query("""
            SELECT DISTINCT p FROM Post p JOIN p.osc o
             WHERE p.osc.id = :oscId
               AND p.ativo = TRUE
               AND o.status = :statusOsc
               AND o.dataArquivamento IS NULL
             ORDER BY p.dataCriacao DESC
            """)
    List<Post> findPublicosPorOsc(
            @Param("oscId") Integer oscId,
            @Param("statusOsc") StatusOsc statusOsc);

    @Query(value = """
            SELECT p.* FROM public.post p JOIN public.oscs o ON o.id = p.id_osc
             WHERE p.id_osc = :oscId AND p.ativo = TRUE AND o.status = :statusOsc
               AND o.data_arquivamento IS NULL
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b
                    WHERE b.active AND ((b.target_type='OSC' AND b.target_id=o.id)
                       OR (b.target_type='POST' AND b.target_id=p.id)))
             ORDER BY p.data_criacao DESC, p.id DESC
            """, nativeQuery = true)
    List<Post> findPublicosPorOscSemBloqueio(@Param("oscId") Integer oscId,
            @Param("statusOsc") String statusOsc);

    List<Post> findByOscCausas(CausaSocial causaSocial);

    @Query("""
            SELECT p FROM Post p
            LEFT JOIN p.osc o
            WHERE p.ativo = true
              AND (p.osc IS NULL OR (o.status = :statusOsc AND o.dataArquivamento IS NULL))
              AND (p.tipo <> com.portoajuda.aplicacao_osc.enums.TipoPost.VOLUNTARIADO OR EXISTS (
                    SELECT v.id FROM Vaga v WHERE v.post = p AND v.status = com.portoajuda.aplicacao_osc.enums.StatusVaga.ABERTA
                      AND v.dataFim >= CURRENT_DATE AND v.dataConclusao IS NULL AND v.quantidadeVagas > 0
                      AND v.quantidadeVagas > (SELECT COUNT(c) FROM CandidaturasVoluntariado c WHERE c.vaga.id=v.id AND c.status=com.portoajuda.aplicacao_osc.enums.StatusCandidatura.APROVADA)))
            """)
    Page<Post> findPublicos(@Param("statusOsc") StatusOsc statusOsc, Pageable pageable);

    @Query(value = """
            SELECT p.* FROM public.post p LEFT JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND (p.id_osc IS NULL OR (o.status=:statusOsc AND o.data_arquivamento IS NULL))
               AND (p.tipo<>'VOLUNTARIADO' OR EXISTS (SELECT 1 FROM public.vagas_voluntariado v WHERE v.id_post=p.id
                    AND v.status='ABERTA' AND v.data_fim>=CURRENT_DATE AND v.data_conclusao IS NULL AND v.quantidade_vagas>0
                    AND v.quantidade_vagas>(SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')))
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                 AND ((b.target_type='OSC' AND b.target_id=o.id) OR (b.target_type='POST' AND b.target_id=p.id)))
            """, countQuery = """
            SELECT count(*) FROM public.post p LEFT JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND (p.id_osc IS NULL OR (o.status=:statusOsc AND o.data_arquivamento IS NULL))
               AND (p.tipo<>'VOLUNTARIADO' OR EXISTS (SELECT 1 FROM public.vagas_voluntariado v WHERE v.id_post=p.id
                    AND v.status='ABERTA' AND v.data_fim>=CURRENT_DATE AND v.data_conclusao IS NULL AND v.quantidade_vagas>0
                    AND v.quantidade_vagas>(SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')))
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                 AND ((b.target_type='OSC' AND b.target_id=o.id) OR (b.target_type='POST' AND b.target_id=p.id)))
            """, nativeQuery = true)
    Page<Post> findPublicosSemBloqueio(@Param("statusOsc") String statusOsc, Pageable pageable);

    @Query(value = """
            SELECT p.* FROM public.post p LEFT JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND (p.id_osc IS NULL OR
                   (o.status=:statusOsc AND o.data_arquivamento IS NULL
                    AND public.ngo_representative_is_publicly_eligible(o.id)))
               AND (p.tipo<>'VOLUNTARIADO' OR EXISTS (SELECT 1 FROM public.vagas_voluntariado v WHERE v.id_post=p.id
                    AND v.status='ABERTA' AND v.data_fim>=CURRENT_DATE AND v.data_conclusao IS NULL AND v.quantidade_vagas>0
                    AND v.quantidade_vagas>(SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')))
             ORDER BY p.data_criacao DESC, p.id DESC
            """, countQuery = """
            SELECT count(*) FROM public.post p LEFT JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND (p.id_osc IS NULL OR
                   (o.status=:statusOsc AND o.data_arquivamento IS NULL
                    AND public.ngo_representative_is_publicly_eligible(o.id)))
               AND (p.tipo<>'VOLUNTARIADO' OR EXISTS (SELECT 1 FROM public.vagas_voluntariado v WHERE v.id_post=p.id
                    AND v.status='ABERTA' AND v.data_fim>=CURRENT_DATE AND v.data_conclusao IS NULL AND v.quantidade_vagas>0
                    AND v.quantidade_vagas>(SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')))
            """, nativeQuery = true)
    Page<Post> findPublicosComVerificacao(@Param("statusOsc") String statusOsc, Pageable pageable);

    @Query(value = """
            SELECT p.* FROM public.post p LEFT JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND (p.id_osc IS NULL OR
                   (o.status=:statusOsc AND o.data_arquivamento IS NULL
                    AND public.ngo_representative_is_publicly_eligible(o.id)))
               AND (p.tipo<>'VOLUNTARIADO' OR EXISTS (SELECT 1 FROM public.vagas_voluntariado v WHERE v.id_post=p.id
                    AND v.status='ABERTA' AND v.data_fim>=CURRENT_DATE AND v.data_conclusao IS NULL AND v.quantidade_vagas>0
                    AND v.quantidade_vagas>(SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')))
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                    AND ((b.target_type='POST' AND b.target_id=p.id) OR
                         (b.target_type='OSC' AND b.target_id=o.id)))
             ORDER BY p.data_criacao DESC, p.id DESC
            """, countQuery = """
            SELECT count(*) FROM public.post p LEFT JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND (p.id_osc IS NULL OR
                   (o.status=:statusOsc AND o.data_arquivamento IS NULL
                    AND public.ngo_representative_is_publicly_eligible(o.id)))
               AND (p.tipo<>'VOLUNTARIADO' OR EXISTS (SELECT 1 FROM public.vagas_voluntariado v WHERE v.id_post=p.id
                    AND v.status='ABERTA' AND v.data_fim>=CURRENT_DATE AND v.data_conclusao IS NULL AND v.quantidade_vagas>0
                    AND v.quantidade_vagas>(SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')))
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                    AND ((b.target_type='POST' AND b.target_id=p.id) OR
                         (b.target_type='OSC' AND b.target_id=o.id)))
            """, nativeQuery = true)
    Page<Post> findPublicosComVerificacaoSemBloqueio(@Param("statusOsc") String statusOsc, Pageable pageable);

    @EntityGraph(attributePaths = {"osc", "vagaVoluntariado"})
    @Query("""
            SELECT p FROM Post p JOIN p.osc o
             WHERE p.ativo = TRUE
               AND p.tipo <> :tipoVoluntariado
               AND o.status = :statusOsc
               AND o.dataArquivamento IS NULL
             ORDER BY p.dataCriacao DESC, p.id DESC
            """)
    Page<Post> findPublicacoesHome(
            @Param("statusOsc") StatusOsc statusOsc,
            @Param("tipoVoluntariado") TipoPost tipoVoluntariado,
            Pageable pageable);

    @Query(value = """
            SELECT p.* FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND p.tipo <> :tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                 AND ((b.target_type='OSC' AND b.target_id=o.id) OR (b.target_type='POST' AND b.target_id=p.id)))
             ORDER BY p.data_criacao DESC, p.id DESC
            """, countQuery = """
            SELECT count(*) FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND p.tipo <> :tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                 AND ((b.target_type='OSC' AND b.target_id=o.id) OR (b.target_type='POST' AND b.target_id=p.id)))
            """, nativeQuery = true)
    Page<Post> findPublicacoesHomeSemBloqueio(@Param("statusOsc") String statusOsc,
            @Param("tipoVoluntariado") String tipoVoluntariado, Pageable pageable);

    @Query(value = """
            SELECT p.* FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND p.tipo <> :tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL
               AND public.ngo_representative_is_publicly_eligible(o.id)
             ORDER BY p.data_criacao DESC, p.id DESC
            """, countQuery = """
            SELECT count(*) FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND p.tipo <> :tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL
               AND public.ngo_representative_is_publicly_eligible(o.id)
            """, nativeQuery = true)
    Page<Post> findPublicacoesHomeVerificadas(@Param("statusOsc") String statusOsc,
            @Param("tipoVoluntariado") String tipoVoluntariado, Pageable pageable);

    @Query(value = """
            SELECT p.* FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND p.tipo <> :tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL
               AND public.ngo_representative_is_publicly_eligible(o.id)
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                    AND ((b.target_type='OSC' AND b.target_id=o.id) OR (b.target_type='POST' AND b.target_id=p.id)))
             ORDER BY p.data_criacao DESC, p.id DESC
            """, countQuery = """
            SELECT count(*) FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
             WHERE p.ativo=TRUE AND p.tipo <> :tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL
               AND public.ngo_representative_is_publicly_eligible(o.id)
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                    AND ((b.target_type='OSC' AND b.target_id=o.id) OR (b.target_type='POST' AND b.target_id=p.id)))
            """, nativeQuery = true)
    Page<Post> findPublicacoesHomeVerificadasSemBloqueio(@Param("statusOsc") String statusOsc,
            @Param("tipoVoluntariado") String tipoVoluntariado, Pageable pageable);

    @EntityGraph(attributePaths = {"osc", "vagaVoluntariado"})
    @Query("""
            SELECT p FROM Post p
              JOIN p.osc o
              JOIN p.vagaVoluntariado v
             WHERE p.ativo = TRUE
               AND p.tipo = :tipoVoluntariado
               AND o.status = :statusOsc
               AND o.dataArquivamento IS NULL
               AND v.status = :statusVaga
               AND v.quantidadeVagas > 0
               AND v.quantidadeVagas > (SELECT COUNT(c) FROM CandidaturasVoluntariado c WHERE c.vaga.id = v.id AND c.status = com.portoajuda.aplicacao_osc.enums.StatusCandidatura.APROVADA)
               AND v.dataFim >= :dataAtual
             ORDER BY p.dataCriacao DESC, p.id DESC
            """)
    Page<Post> findVoluntariadosHome(
            @Param("statusOsc") StatusOsc statusOsc,
            @Param("tipoVoluntariado") TipoPost tipoVoluntariado,
            @Param("statusVaga") StatusVaga statusVaga,
            @Param("dataAtual") java.time.LocalDate dataAtual,
            Pageable pageable);

    @Query(value = """
            SELECT p.* FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
              JOIN public.vagas_voluntariado v ON v.id_post=p.id
             WHERE p.ativo=TRUE AND p.tipo=:tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL AND v.status=:statusVaga
               AND v.quantidade_vagas>0 AND v.data_fim>=:dataAtual
               AND v.quantidade_vagas > (SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                 AND ((b.target_type='OSC' AND b.target_id=o.id) OR (b.target_type='POST' AND b.target_id=p.id)))
             ORDER BY p.data_criacao DESC, p.id DESC
            """, countQuery = """
            SELECT count(*) FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
              JOIN public.vagas_voluntariado v ON v.id_post=p.id
             WHERE p.ativo=TRUE AND p.tipo=:tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL AND v.status=:statusVaga
               AND v.quantidade_vagas>0 AND v.data_fim>=:dataAtual
               AND v.quantidade_vagas > (SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                 AND ((b.target_type='OSC' AND b.target_id=o.id) OR (b.target_type='POST' AND b.target_id=p.id)))
            """, nativeQuery = true)
    Page<Post> findVoluntariadosHomeSemBloqueio(@Param("statusOsc") String statusOsc,
            @Param("tipoVoluntariado") String tipoVoluntariado, @Param("statusVaga") String statusVaga,
            @Param("dataAtual") java.time.LocalDate dataAtual, Pageable pageable);

    @Query(value = """
            SELECT p.* FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
              JOIN public.vagas_voluntariado v ON v.id_post=p.id
             WHERE p.ativo=TRUE AND p.tipo=:tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL AND v.status=:statusVaga
               AND v.quantidade_vagas>0 AND v.data_fim>=:dataAtual
               AND v.quantidade_vagas > (SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')
               AND public.ngo_representative_is_publicly_eligible(o.id)
             ORDER BY p.data_criacao DESC, p.id DESC
            """, countQuery = """
            SELECT count(*) FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
              JOIN public.vagas_voluntariado v ON v.id_post=p.id
             WHERE p.ativo=TRUE AND p.tipo=:tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL AND v.status=:statusVaga
               AND v.quantidade_vagas>0 AND v.data_fim>=:dataAtual
               AND v.quantidade_vagas > (SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')
               AND public.ngo_representative_is_publicly_eligible(o.id)
            """, nativeQuery = true)
    Page<Post> findVoluntariadosHomeVerificados(@Param("statusOsc") String statusOsc,
            @Param("tipoVoluntariado") String tipoVoluntariado, @Param("statusVaga") String statusVaga,
            @Param("dataAtual") java.time.LocalDate dataAtual, Pageable pageable);

    @Query(value = """
            SELECT p.* FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
              JOIN public.vagas_voluntariado v ON v.id_post=p.id
             WHERE p.ativo=TRUE AND p.tipo=:tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL AND v.status=:statusVaga
               AND v.quantidade_vagas>0 AND v.data_fim>=:dataAtual
               AND v.quantidade_vagas > (SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')
               AND public.ngo_representative_is_publicly_eligible(o.id)
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                    AND ((b.target_type='OSC' AND b.target_id=o.id) OR (b.target_type='POST' AND b.target_id=p.id)))
             ORDER BY p.data_criacao DESC, p.id DESC
            """, countQuery = """
            SELECT count(*) FROM public.post p JOIN public.oscs o ON o.id=p.id_osc
              JOIN public.vagas_voluntariado v ON v.id_post=p.id
             WHERE p.ativo=TRUE AND p.tipo=:tipoVoluntariado AND o.status=:statusOsc
               AND o.data_arquivamento IS NULL AND v.status=:statusVaga
               AND v.quantidade_vagas>0 AND v.data_fim>=:dataAtual
               AND v.quantidade_vagas > (SELECT count(*) FROM public.candidaturas_voluntariado c WHERE c.id_vaga=v.id AND c.status='APROVADA')
               AND public.ngo_representative_is_publicly_eligible(o.id)
               AND NOT EXISTS (SELECT 1 FROM public.content_moderation_blocks b WHERE b.active
                    AND ((b.target_type='OSC' AND b.target_id=o.id) OR (b.target_type='POST' AND b.target_id=p.id)))
            """, nativeQuery = true)
    Page<Post> findVoluntariadosHomeVerificadosSemBloqueio(@Param("statusOsc") String statusOsc,
            @Param("tipoVoluntariado") String tipoVoluntariado, @Param("statusVaga") String statusVaga,
            @Param("dataAtual") java.time.LocalDate dataAtual, Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM Post p
            JOIN p.osc o
            JOIN o.registrosCausas causa
            WHERE p.ativo = true
              AND o.status = :statusOsc
              AND o.dataArquivamento IS NULL
              AND (causa.causa = :causaSocial OR causa.causaPrincipal = :causaSocial)
            """)
    List<Post> findPublicosPorCausaSocial(
            @Param("causaSocial") CausaSocial causaSocial,
            @Param("statusOsc") StatusOsc statusOsc);
}
