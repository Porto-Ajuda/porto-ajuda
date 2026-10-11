
package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.CodigoAutenticacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface CodigoAutenticacaoRepository
                extends JpaRepository<CodigoAutenticacao, Long> {

        Optional<CodigoAutenticacao> findByCodigoHash(String codigoHash);

        @Modifying
        @Transactional
        @Query("""
                        UPDATE CodigoAutenticacao c
                        SET c.utilizado = true
                        WHERE c.codigoHash = :hash
                          AND c.utilizado = false
                          AND c.expiraEm > :agora
                        """)
        int consumirSeValido(
                        @Param("hash") String hash,
                        @Param("agora") Instant agora);

        @Modifying
        @Transactional
        @Query("""
                        DELETE FROM CodigoAutenticacao c
                        WHERE c.usuario.id = :usuarioId
                        """)
        int deleteByUsuarioId(
                        @Param("usuarioId") Integer usuarioId);
}
