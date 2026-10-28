
package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SessaoUsuarioRepository
        extends JpaRepository<SessaoUsuario, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SessaoUsuario> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sessao from SessaoUsuario sessao where sessao.id = :id")
    Optional<SessaoUsuario> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
            DELETE FROM SessaoUsuario s
            WHERE s.expiraEm <= :agora
                OR s.revogadaEm IS NOT NULL
                OR (
                    s.lembrarDeMim = true
                    AND s.ultimaAtividadeEm <= :limiteInatividade
                )
                OR s.usuario.ativo = false
            """)
    int excluirSessoesInvalidas(
            @Param("agora") Instant agora,
            @Param("limiteInatividade") Instant limiteInatividade);

    @Modifying
    @Query("""
            UPDATE SessaoUsuario s
            SET s.revogadaEm = :agora
            WHERE s.usuario.id = :usuarioId
                AND s.revogadaEm IS NULL
            """)
    int revogarSessoesPorUsuarioId(
            @Param("usuarioId") Integer usuarioId,
            @Param("agora") Instant agora);

    @Modifying
    @Query("DELETE FROM SessaoUsuario s WHERE s.usuario.id = :usuarioId")
    int excluirPorUsuarioId(@Param("usuarioId") Integer usuarioId);
}
