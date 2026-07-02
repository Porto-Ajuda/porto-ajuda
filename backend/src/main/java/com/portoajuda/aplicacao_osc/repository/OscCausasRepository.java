package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.OscCausas;
import com.portoajuda.aplicacao_osc.entity.OscCausasId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OscCausasRepository extends JpaRepository<OscCausas, OscCausasId> {
}
