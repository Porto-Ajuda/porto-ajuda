package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.PhoneVerificationChallenge;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhoneVerificationChallengeRepository
        extends JpaRepository<PhoneVerificationChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PhoneVerificationChallenge c where c.id = :id")
    Optional<PhoneVerificationChallenge> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("""
            update PhoneVerificationChallenge c
               set c.state = 'INVALIDATED', c.invalidatedAt = :now, c.codeHash = null
             where c.usuario.id = :userId
               and c.state in ('ISSUING', 'PENDING')
            """)
    int invalidatePendingForUser(@Param("userId") Integer userId, @Param("now") Instant now);
}
