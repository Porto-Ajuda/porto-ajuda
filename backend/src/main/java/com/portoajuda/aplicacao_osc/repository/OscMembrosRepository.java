package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.OscMembroId;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OscMembrosRepository extends JpaRepository<OscMembros, OscMembroId> {
}
