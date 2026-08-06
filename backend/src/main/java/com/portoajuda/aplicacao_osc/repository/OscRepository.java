package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.Email;
import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OscRepository extends JpaRepository<Osc, Integer> {
    Optional<Osc> findByCnpj(Cnpj cnpj);

    boolean existsByIdAndUsuarioId(Integer id, Integer usuarioId);

    @Query("""
          SELECT COUNT(m) > 0
          FROM OscMembros m
          WHERE m.usuario.id = :usuarioId
          AND m.osc.id = :oscId   
    """)
    boolean userBelongsOsc(Integer oscId, Integer usuarioId);

    boolean existsByCnpj(Cnpj cnpj);
}