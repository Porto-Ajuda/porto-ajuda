
package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.CandidaturasVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidaturasVoluntariadoRepository
        extends JpaRepository<CandidaturasVoluntariado, Integer> {

    boolean existsByVaga_IdAndUsuario_IdAndStatusIn(
            Integer vagaId, Integer usuarioId, java.util.Collection<StatusCandidatura> status);

    long countByVaga_IdAndStatus(Integer vagaId, StatusCandidatura status);

    java.util.Optional<CandidaturasVoluntariado> findFirstByVaga_IdAndUsuario_IdOrderByDataCandidaturaDesc(
            Integer vagaId, Integer usuarioId);

    @EntityGraph(attributePaths = {"vaga", "vaga.post", "vaga.post.osc"})
    java.util.List<CandidaturasVoluntariado> findAllByUsuario_IdOrderByDataCandidaturaDesc(Integer usuarioId);

    @EntityGraph(attributePaths = {"vaga", "vaga.post", "vaga.post.osc"})
    Page<CandidaturasVoluntariado> findAllByUsuario_IdOrderByDataCandidaturaDesc(Integer usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = {"vaga", "vaga.post", "usuario"})
    @Query(value = """
            SELECT c FROM CandidaturasVoluntariado c
             WHERE c.vaga.post.osc.id = :oscId
               AND (:status IS NULL OR c.status = :status)
             ORDER BY c.dataCandidatura DESC, c.id DESC
            """,
            countQuery = """
            SELECT COUNT(c) FROM CandidaturasVoluntariado c
             WHERE c.vaga.post.osc.id = :oscId
               AND (:status IS NULL OR c.status = :status)
            """)
    Page<CandidaturasVoluntariado> listarPorOscEStatus(
            @Param("oscId") Integer oscId,
            @Param("status") StatusCandidatura status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"vaga", "vaga.post", "usuario"})
    java.util.Optional<CandidaturasVoluntariado> findByIdAndVaga_Post_Osc_Id(Integer id, Integer oscId);

    @EntityGraph(attributePaths = {"vaga", "vaga.post", "vaga.post.osc"})
    java.util.Optional<CandidaturasVoluntariado> findByIdAndUsuario_Id(Integer id, Integer usuarioId);

    boolean existsByVaga_Id(Integer vagaId);

    @Modifying
    @Query("""
                DELETE FROM CandidaturasVoluntariado c
                WHERE c.usuario.id = :usuarioId
            """)
    int excluirPorUsuarioId(@Param("usuarioId") Integer usuarioId);

    @Modifying
    @Query("""
                DELETE FROM CandidaturasVoluntariado c
                WHERE c.vaga.post.osc.id = :oscId
            """)
    int excluirPorOscId(@Param("oscId") Integer oscId);
}
