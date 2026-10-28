
package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.PreferenciasAcessibilidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PreferenciasAcessibilidadeRepository
        extends JpaRepository<PreferenciasAcessibilidade, Integer> {

    Optional<PreferenciasAcessibilidade> findByUsuario_Id(
        Integer usuarioId
    );

    @Modifying
    @Query("DELETE FROM PreferenciasAcessibilidade p WHERE p.usuario.id = :usuarioId")
    int excluirPorUsuarioId(@Param("usuarioId") Integer usuarioId);
}
