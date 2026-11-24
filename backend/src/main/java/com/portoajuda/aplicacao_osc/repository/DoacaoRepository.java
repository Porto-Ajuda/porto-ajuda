package com.portoajuda.aplicacao_osc.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.portoajuda.aplicacao_osc.entity.Doacao;

import jakarta.persistence.LockModeType;

public interface DoacaoRepository extends JpaRepository<Doacao, Integer> {

    boolean existsByUsuario_Id(Integer usuarioId);

    boolean existsByOsc_Id(Integer oscId);

    boolean existsByPost_Osc_Id(Integer oscId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doacao d where d.stripeSessionId = :sessionId")
    Optional<Doacao> findByStripeSessionIdForUpdate(@Param("sessionId") String sessionId);

    @Modifying
    @Query(value = "INSERT INTO doacao_stripe_events(event_id,event_type) VALUES (:eventId,:eventType) ON CONFLICT(event_id) DO NOTHING", nativeQuery = true)
    int registrarEventoStripe(@Param("eventId") String eventId, @Param("eventType") String eventType);
}
