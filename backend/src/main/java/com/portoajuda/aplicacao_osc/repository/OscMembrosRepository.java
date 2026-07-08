package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.OscMembroId;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OscMembrosRepository extends JpaRepository<OscMembros, OscMembroId> {
}
