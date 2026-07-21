package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OscRepository extends JpaRepository<Osc, Integer> {
    Optional<Osc> findByCnpj(Cnpj cnpj);
    boolean existsByCnpj(Cnpj cnpj);
}