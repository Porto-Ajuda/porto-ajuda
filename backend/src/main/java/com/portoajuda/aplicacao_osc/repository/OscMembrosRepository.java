    package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.OscMembroId;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.utils.Email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OscMembrosRepository
        extends JpaRepository<OscMembros, OscMembroId> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "osc", "usuario" })
    List<OscMembros> findAllByUsuario_IdOrderByOsc_NomeAsc(Integer usuarioId);

    Optional<OscMembros> findByOsc_IdAndUsuario_Id(
            Integer oscId,
            Integer usuarioId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "usuario")
    List<OscMembros> findAllByOsc_IdOrderByUsuario_NomeAsc(Integer oscId);

    Optional<OscMembros> findByOsc_IdAndUsuario_Email(
            Integer oscId,
            Email email);

    @Modifying
    @Query("""
                DELETE FROM OscMembros membro
                WHERE membro.usuario.id = :usuarioId
            """)
    int excluirPorUsuarioId(
            @Param("usuarioId") Integer usuarioId);

    @Modifying
    @Query("""
                DELETE FROM OscMembros membro
                WHERE membro.osc.id = :oscId
            """)
    int excluirPorOscId(
            @Param("oscId") Integer oscId);
}
