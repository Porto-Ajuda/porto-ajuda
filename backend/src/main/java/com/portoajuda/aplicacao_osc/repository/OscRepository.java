package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OscRepository extends JpaRepository<Osc, Integer> {

    Page<Osc> findAllByStatus(StatusOsc status, Pageable pageable);

    Page<Osc> findAllByStatusAndDataArquivamentoIsNull(StatusOsc status, Pageable pageable);

    long countByStatusAndDataArquivamentoIsNull(StatusOsc status);

    long countByDataArquivamentoIsNotNull();

    Optional<Osc> findByCnpj(Cnpj cnpj);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Osc o WHERE o.id = :oscId")
    Optional<Osc> findForUpdateById(@Param("oscId") Integer oscId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Osc o WHERE o.cnpj = :cnpj")
    Optional<Osc> findForUpdateByCnpj(@Param("cnpj") Cnpj cnpj);

    Optional<Osc> findByUsuarioIdAndDataArquivamentoIsNull(Integer usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Osc> findAllByUsuario_Id(Integer usuarioId);

    List<Osc> findAllByUsuario_IdOrderByNomeAsc(Integer usuarioId);

    boolean existsByIdAndUsuarioId(Integer id, Integer usuarioId);

    @Query("""
                SELECT COUNT(m) > 0
                FROM OscMembros m
                WHERE m.usuario.id = :usuarioId
                  AND m.osc.id = :oscId
            """)
    boolean userBelongsOsc(
            @Param("oscId") Integer oscId,
            @Param("usuarioId") Integer usuarioId);

    boolean existsByCnpj(Cnpj cnpj);
}
