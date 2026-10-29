package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    List<Post> findByTipo(TipoPost tipo);

    List<Post> findByOscId(Integer oscId);

    List<Post> findByOscCausas(CausaSocial causaSocial);
}
