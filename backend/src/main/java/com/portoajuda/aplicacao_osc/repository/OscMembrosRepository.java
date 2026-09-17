package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.OscMembroId;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.utils.Email;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OscMembrosRepository extends JpaRepository<OscMembros, OscMembroId> {
    @Query("""
        SELECT m
        FROM OscMembros m
        JOIN m.usuario u
        WHERE u.email = :email
    """)
    Optional<OscMembros> findByUsuarioEmail(@Param("email")Email email);
}