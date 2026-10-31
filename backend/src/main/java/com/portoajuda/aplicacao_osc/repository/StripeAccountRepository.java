package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.StripeAccounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StripeAccountRepository extends JpaRepository<StripeAccounts, Long> {
    boolean existsByOscId(Integer oscId);

    Optional<StripeAccounts> findByOscId(Integer oscId);
}