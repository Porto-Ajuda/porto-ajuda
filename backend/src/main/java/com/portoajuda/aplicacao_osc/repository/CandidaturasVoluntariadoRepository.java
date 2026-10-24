
package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.CandidaturasVoluntariado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidaturasVoluntariadoRepository
        extends JpaRepository<CandidaturasVoluntariado, Integer> {

    @Modifying
    @Query("""
                DELETE FROM CandidaturasVoluntariado c
                WHERE c.usuario.id = :usuarioId
            """)
    int excluirPorUsuarioId(@Param("usuarioId") Integer usuarioId);

    @Modifying
    @Query("""
                DELETE FROM CandidaturasVoluntariado c
                WHERE c.vaga.post.osc.id = :oscId
            """)
    int excluirPorOscId(@Param("oscId") Integer oscId);
}
