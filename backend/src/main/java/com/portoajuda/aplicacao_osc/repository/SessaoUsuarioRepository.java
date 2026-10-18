package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessaoUsuarioRepository
        extends JpaRepository<SessaoUsuario, Long> {

    Optional<SessaoUsuario> findByTokenHash(String tokenHash);
}