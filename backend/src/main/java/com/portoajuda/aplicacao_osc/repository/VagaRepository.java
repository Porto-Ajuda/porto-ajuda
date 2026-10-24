package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Vaga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VagaRepository extends JpaRepository<Vaga, Integer> {

    @Modifying
    @Query("DELETE FROM Vaga v WHERE v.post.osc.id = :oscId")
    int excluirPorOscId(@Param("oscId") Integer oscId);
}
