
package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.utils.Email;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByEmail(Email email);

    boolean existsByEmail(Email email);

    List<Usuario> findByEmailVerificadoFalseAndDataCriacaoBefore(
            LocalDateTime limite);

    @Modifying
    @Query(value = """
            DELETE FROM usuario_roles
            WHERE id_usuario = :usuarioId
            """, nativeQuery = true)
    int removerAssociacoesRoles(
            @Param("usuarioId") Integer usuarioId);

    @Query("""
            SELECT COUNT(m) > 0
            FROM OscMembros m
            WHERE m.usuario.id = :usuarioId
            """)
    boolean usuarioIsMember(Integer usuarioId);
}
