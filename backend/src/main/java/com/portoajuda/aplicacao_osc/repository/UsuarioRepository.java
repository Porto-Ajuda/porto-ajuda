
package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.utils.Email;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

        Optional<Usuario> findByEmail(Email email);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select usuario from Usuario usuario where usuario.id = :usuarioId")
        Optional<Usuario> findByIdForUpdate(@Param("usuarioId") Integer usuarioId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select usuario from Usuario usuario where usuario.email = :email")
        Optional<Usuario> findByEmailForUpdate(@Param("email") Email email);

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

        @Modifying
        @Query("DELETE FROM Usuario usuario WHERE usuario.id = :usuarioId")
        int excluirPorIdSemCascade(@Param("usuarioId") Integer usuarioId);

        @Query("""
                        SELECT COUNT(m) > 0
                        FROM OscMembros m
                        WHERE m.usuario.id = :usuarioId
                        """)
        boolean usuarioIsMember(Integer usuarioId);

        List<Usuario> findByAtivoFalseAndDataSolicitacaoExclusaoIsNotNullAndDataExclusaoAgendadaLessThanEqual(
                        LocalDateTime agora);
}
