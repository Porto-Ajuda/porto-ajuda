package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.PostCausaId;
import com.portoajuda.aplicacao_osc.entity.PostCausas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostCausasRepository extends JpaRepository<PostCausas, PostCausaId> {

    @Modifying
    @Query("DELETE FROM PostCausas pc WHERE pc.post.osc.id = :oscId")
    int excluirPorOscId(@Param("oscId") Integer oscId);
}
