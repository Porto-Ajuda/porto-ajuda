package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.StripeAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StripeAccountRepository extends JpaRepository<StripeAccount, Long> {
    boolean existsByOscId(Integer oscId);

    Optional<StripeAccount> findByOscId(Integer oscId);
}