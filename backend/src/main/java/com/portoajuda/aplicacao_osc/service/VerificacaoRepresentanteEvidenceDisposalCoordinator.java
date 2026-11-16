package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.VerificacaoRepresentanteJdbcRepository.DisposalTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Internal, explicit disposal coordinator. It is not exposed by a controller and is not scheduled.
 * The caller must hold an authorized ADMIN_SESSION; the service rechecks the verification permission.
 */
@Service
@RequiredArgsConstructor
public class VerificacaoRepresentanteEvidenceDisposalCoordinator {
    private final VerificacaoRepresentanteService verification;
    private final PrivateVerificationEvidenceStorage storage;

    public void disposeEvidence(long evidenceId, String justification, Usuario reviewer) {
        DisposalTicket ticket = verification.requestEvidenceDisposal(evidenceId, justification, reviewer);
        if (ticket.storageKey() == null || ticket.storageKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "A evidência já não está disponível para um novo descarte.");
        }
        try {
            storage.delete(ticket.storageKey());
        } catch (RuntimeException storageFailure) {
            verification.recordEvidenceDisposalFailure(evidenceId, ticket.eventId(), reviewer);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O arquivo continua privado; o descarte ficou pendente de nova tentativa.");
        }
        verification.completeEvidenceDisposal(evidenceId, ticket.eventId(), reviewer);
    }
}
