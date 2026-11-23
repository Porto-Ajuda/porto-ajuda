package com.portoajuda.aplicacao_osc.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portoajuda.aplicacao_osc.entity.StripeAccounts;

public interface StripeAccountRepository extends JpaRepository<StripeAccounts, Long> {
    boolean existsByOscId(Integer oscId);

    Optional<StripeAccounts> findByOscId(Integer oscId);

}