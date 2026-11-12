package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.RecuperacaoSenha;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, Long> {
    @Query(value = "select pg_advisory_xact_lock(:lockKey) is null", nativeQuery = true)
    boolean lockRateKey(@Param("lockKey") long lockKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecuperacaoSenha r where r.tokenHash = :tokenHash")
    Optional<RecuperacaoSenha> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<RecuperacaoSenha> findByTokenHash(String tokenHash);

    @Query("select count(r) from RecuperacaoSenha r where r.solicitadoEm >= :since and (r.emailKey = :emailKey or r.ipKey = :ipKey)")
    long countRecent(@Param("emailKey") String emailKey, @Param("ipKey") String ipKey,
            @Param("since") Instant since);

    @Modifying
    @Query("delete from RecuperacaoSenha r where r.solicitadoEm < :before")
    int deleteOlderThan(@Param("before") Instant before);

    @Modifying
    @Query("update RecuperacaoSenha r set r.consumidoEm = :now where r.usuario.id = :userId and r.consumidoEm is null")
    int invalidateForUser(@Param("userId") Integer userId, @Param("now") Instant now);
}
