package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery;
import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery.State;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuthDeliveryRepository extends JpaRepository<AdminAuthDelivery, UUID> {

    @Query(value = """
            SELECT d.*
              FROM public.admin_auth_deliveries d
              JOIN public.admin_auth_challenges c ON c.id = d.challenge_id
             WHERE c.state = 'ISSUING'
               AND c.expires_at > CURRENT_TIMESTAMP
               AND d.attempt_count < 3
               AND (d.state = 'PENDING'
                    OR (d.state = 'PROCESSING' AND d.lease_until <= CURRENT_TIMESTAMP))
             ORDER BY d.created_at, d.id
             FOR UPDATE OF d SKIP LOCKED
             LIMIT 1
            """, nativeQuery = true)
    Optional<AdminAuthDelivery> claimNextPending();

    long countByChallenge_IdAndState(UUID challengeId, State state);

    List<AdminAuthDelivery> findAllByChallenge_Id(UUID challengeId);

    @Modifying
    @Query("""
            update AdminAuthDelivery d
               set d.state = :failedState,
                   d.encryptedCode = null,
                   d.leaseUntil = null,
                   d.updatedAt = :now
             where d.challenge.id = :challengeId
               and d.state in :activeStates
            """)
    int failChallengeDeliveries(
            @Param("challengeId") UUID challengeId,
            @Param("activeStates") List<State> activeStates,
            @Param("failedState") State failedState,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            update AdminAuthDelivery d
               set d.state = :failedState,
                   d.encryptedCode = null,
                   d.leaseUntil = null,
                   d.updatedAt = :now
             where d.challenge.usuario.id = :userId
               and d.challenge.state = :invalidatedState
               and d.state in :activeStates
            """)
    int clearInvalidatedForUser(
            @Param("userId") Integer userId,
            @Param("activeStates") List<State> activeStates,
            @Param("failedState") State failedState,
            @Param("invalidatedState") com.portoajuda.aplicacao_osc.entity.AdminAuthChallenge.State invalidatedState,
            @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE public.admin_auth_deliveries d
               SET state = 'FAILED', encrypted_code = NULL, lease_until = NULL,
                   updated_at = :now
              FROM public.admin_auth_challenges c
             WHERE c.id = d.challenge_id
               AND c.state = 'ISSUING'
               AND c.expires_at <= :now
               AND d.state IN ('PENDING', 'PROCESSING')
            """, nativeQuery = true)
    int expireDeliveries(@Param("now") Instant now);

    @Query(value = """
            SELECT DISTINCT c.id
              FROM public.admin_auth_challenges c
              JOIN public.admin_auth_deliveries d ON d.challenge_id = c.id
             WHERE c.state = 'ISSUING'
               AND c.expires_at <= :now
            """, nativeQuery = true)
    List<UUID> findExpiredIssuingChallengeIds(@Param("now") Instant now);

    @Query(value = """
            SELECT DISTINCT c.id
              FROM public.admin_auth_challenges c
              JOIN public.admin_auth_deliveries d ON d.challenge_id = c.id
             WHERE c.state = 'ISSUING'
               AND d.attempt_count >= 3
               AND d.state = 'PROCESSING'
               AND d.lease_until <= :now
            """, nativeQuery = true)
    List<UUID> findExhaustedIssuingChallengeIds(@Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE public.admin_auth_deliveries d
               SET state = 'FAILED', encrypted_code = NULL, lease_until = NULL,
                   updated_at = :now
              FROM public.admin_auth_challenges c
             WHERE c.id = d.challenge_id
               AND c.state = 'ISSUING'
               AND d.attempt_count >= 3
               AND d.state IN ('PENDING', 'PROCESSING')
               AND (d.lease_until IS NULL OR d.lease_until <= :now)
            """, nativeQuery = true)
    int failExhaustedLeases(@Param("now") Instant now);
}
