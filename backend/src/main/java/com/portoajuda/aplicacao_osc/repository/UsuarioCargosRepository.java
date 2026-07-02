package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.UsuarioCargoId;
import com.portoajuda.aplicacao_osc.entity.UsuarioCargos;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioCargosRepository extends JpaRepository<UsuarioCargos, UsuarioCargoId> {
}
