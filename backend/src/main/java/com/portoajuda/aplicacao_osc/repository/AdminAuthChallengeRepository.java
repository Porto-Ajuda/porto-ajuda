package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuthChallengeRepository extends JpaRepository<AdminAuthChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AdminAuthChallenge c where c.id = :id")
    Optional<AdminAuthChallenge> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("""
            update AdminAuthChallenge c
               set c.state = :invalidatedState,
                   c.invalidatedAt = :now,
                   c.emailCodeHash = null,
                   c.smsCodeHash = null
             where c.usuario.id = :userId
               and c.state in :activeStates
            """)
    int invalidatePendingForUser(
            @Param("userId") Integer userId,
            @Param("now") Instant now,
            @Param("invalidatedState") AdminAuthChallenge.State invalidatedState,
            @Param("activeStates") List<AdminAuthChallenge.State> activeStates);
}
