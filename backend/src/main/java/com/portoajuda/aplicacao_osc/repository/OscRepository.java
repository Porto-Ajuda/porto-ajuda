package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Osc;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OscRepository extends JpaRepository<Osc, Integer> {
}