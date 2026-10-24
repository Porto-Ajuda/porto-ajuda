package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Integer> {

    @Modifying
    @Query("UPDATE Post p SET p.usuario = null WHERE p.usuario.id = :usuarioId")
    int desvincularAutorPorUsuarioId(@Param("usuarioId") Integer usuarioId);

    @Modifying
    @Query("DELETE FROM Post p WHERE p.osc.id = :oscId")
    int excluirPorOscId(@Param("oscId") Integer oscId);
}
