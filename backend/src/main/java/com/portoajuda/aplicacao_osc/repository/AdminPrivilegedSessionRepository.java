package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.AdminPrivilegedSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminPrivilegedSessionRepository
        extends JpaRepository<AdminPrivilegedSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AdminPrivilegedSession s where s.credentialHash = :credentialHash")
    Optional<AdminPrivilegedSession> findByCredentialHashForUpdate(
            @Param("credentialHash") String credentialHash);

    @Modifying
    @Query("""
            update AdminPrivilegedSession s
               set s.revokedAt = :now, s.revocationReason = :reason
             where s.usuario.id = :userId
               and s.revokedAt is null
            """)
    int revokeAllForUser(
            @Param("userId") Integer userId,
            @Param("now") Instant now,
            @Param("reason") String reason);
}
