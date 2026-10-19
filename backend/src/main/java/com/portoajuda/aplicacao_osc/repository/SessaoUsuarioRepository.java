package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SessaoUsuarioRepository
        extends JpaRepository<SessaoUsuario, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SessaoUsuario> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sessao from SessaoUsuario sessao where sessao.id = :id")
    Optional<SessaoUsuario> findByIdForUpdate(@Param("id") Long id);
}
