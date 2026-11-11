package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface VagaRepository extends JpaRepository<Vaga, Integer> {
    Optional<Vaga> findByPost(Post post);

    /** Serializes every application and decision for this vacancy on PostgreSQL. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vaga v JOIN FETCH v.post p JOIN FETCH p.osc WHERE v.id = :vagaId")
    Optional<Vaga> findForUpdateById(@Param("vagaId") Integer vagaId);

    @Modifying
    @Query("DELETE FROM Vaga v WHERE v.post.osc.id = :oscId")
    int excluirPorOscId(@Param("oscId") Integer oscId);
}
