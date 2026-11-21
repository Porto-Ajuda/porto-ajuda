package com.portoajuda.aplicacao_osc.support;

import com.portoajuda.aplicacao_osc.dto.request.RequestCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.response.CandidaturaUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.CandidaturaVoluntariadoService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Persisted, actor-bound preview and explicit-confirmation flow for one volunteer application action. */
@Service
public class SupportVolunteerApplicationActionService {
    private static final Duration DRAFT_TTL = Duration.ofMinutes(5);
    private final SupportVolunteerApplicationDraftRepository drafts;
    private final SupportConversationRepository conversations;
    private final SupportPublicContentSearch publicSearch;
    private final CandidaturaVoluntariadoService applications;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Autowired
    public SupportVolunteerApplicationActionService(SupportVolunteerApplicationDraftRepository drafts,
            SupportConversationRepository conversations, SupportPublicContentSearch publicSearch,
            CandidaturaVoluntariadoService applications, PlatformTransactionManager transactionManager) {
        this(drafts, conversations, publicSearch, applications, transactionManager, Clock.systemUTC());
    }

    SupportVolunteerApplicationActionService(SupportVolunteerApplicationDraftRepository drafts,
            SupportConversationRepository conversations, SupportPublicContentSearch publicSearch,
            CandidaturaVoluntariadoService applications, PlatformTransactionManager transactionManager, Clock clock) {
        this.drafts = drafts;
        this.conversations = conversations;
        this.publicSearch = publicSearch;
        this.applications = applications;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public SupportDtos.VolunteerApplicationDraft createDraft(Usuario actor, UUID conversationId,
            Integer opportunityId, String modelTitle, String modelSummary) {
        int actorId = requireUser(actor);
        Instant now = clock.instant();
        return transaction.execute(status -> {
            SupportConversationRepository.Row conversation = ownedConversation(conversationId, actorId);
            requireAiMayAct(conversation);
            SupportDtos.SearchResult current = publicSearch.publicVolunteerOpportunity(opportunityId);
            if (current == null) throw conflict("Essa oportunidade não está mais disponível para candidatura.");
            var available = applications.disponibilidadePublica(opportunityId);
            if (available == null || !available.aceitaCandidaturas()) throw conflict("Essa oportunidade não tem vaga disponível no momento.");
            var existing = applications.situacaoMinhaCandidatura(opportunityId, actor);
            if (existing.jaSeCandidatou()) throw conflict("Você já possui uma candidatura para essa oportunidade.");
            String safeTitle = bounded(current.title(), 255);
            String safeSummary = bounded(current.summary(), 1200);
            String ngoName = ngoName(safeSummary);
            String hash = bindingHash(actorId, conversationId, opportunityId);
            return drafts.createOrGet(UUID.randomUUID(), actorId, conversationId, opportunityId, hash,
                    safeTitle.isBlank() ? bounded(modelTitle, 255) : safeTitle, ngoName,
                    safeSummary.isBlank() ? bounded(modelSummary, 1200) : safeSummary,
                    now.plus(DRAFT_TTL));
        });
    }

    public List<SupportDtos.VolunteerApplicationDraft> pending(Usuario actor, UUID conversationId) {
        int actorId = requireUser(actor);
        return transaction.execute(status -> {
            ownedConversation(conversationId, actorId);
            return drafts.openFor(actorId, conversationId, clock.instant());
        });
    }

    public SupportDtos.VolunteerApplicationDraft cancel(Usuario actor, UUID conversationId, UUID draftId,
            String expectedHash) {
        int actorId = requireUser(actor);
        return transaction.execute(status -> {
            ownedConversation(conversationId, actorId);
            SupportDtos.VolunteerApplicationDraft draft = ownedDraft(actorId, conversationId, draftId);
            verifyHash(draft, expectedHash);
            if ("DRAFT".equals(draft.state())) drafts.cancel(draftId);
            return new SupportDtos.VolunteerApplicationDraft(draft.id(), draft.opportunityId(), draft.title(),
                    draft.ngoName(), draft.details(), draft.requestHash(), draft.expiresAt(),
                    "DRAFT".equals(draft.state()) ? "CANCELLED" : draft.state(), draft.applicationId(),
                    draft.applicationStatus(), "Rascunho cancelado. Nenhuma candidatura foi enviada.");
        });
    }

    public SupportDtos.VolunteerApplicationDraft confirm(Usuario actor, UUID conversationId, UUID draftId,
            String expectedHash) {
        int actorId = requireUser(actor);
        return transaction.execute(status -> {
            SupportConversationRepository.Row conversation = ownedConversation(conversationId, actorId);
            requireAiMayAct(conversation);
            SupportDtos.VolunteerApplicationDraft draft = ownedDraftForUpdate(actorId, conversationId, draftId);
            verifyHash(draft, expectedHash);
            if ("COMPLETED".equals(draft.state())) return withMessage(draft, successMessage(draft.applicationStatus()));
            if (!"DRAFT".equals(draft.state())) throw conflict("Esta prévia não está mais ativa. Peça uma nova prévia à Sophie.");
            if (!clock.instant().isBefore(draft.expiresAt())) {
                drafts.expire(draftId);
                return new SupportDtos.VolunteerApplicationDraft(draft.id(), draft.opportunityId(), draft.title(),
                        draft.ngoName(), draft.details(), draft.requestHash(), draft.expiresAt(), "EXPIRED",
                        null, null, "Esta prévia expirou. Peça à Sophie para conferir a disponibilidade novamente.");
            }

            // The catalog lookup, public eligibility, moderation and accepted-slot check are repeated
            // through their source services immediately before mutation. `candidatar` locks the vacancy.
            SupportDtos.SearchResult current = publicSearch.publicVolunteerOpportunity(draft.opportunityId());
            if (current == null) throw conflict("Essa oportunidade deixou de estar pública ou elegível.");
            CandidaturaUsuarioDTO result = applications.candidatar(draft.opportunityId(),
                    new RequestCandidaturaDTO(null), actor);
            if (drafts.complete(draftId, result.id(), result.status().name(), clock.instant()) != 1) {
                throw conflict("A prévia já foi processada. Atualize a conversa para conferir o resultado.");
            }
            String outcome = successMessage(result.status().name());
            conversations.addMessage(conversationId, "AI", null, "Sophie · confirmação da ação", outcome,
                    "volunteer-application-completed:" + draftId);
            return new SupportDtos.VolunteerApplicationDraft(draft.id(), draft.opportunityId(), draft.title(),
                    draft.ngoName(), draft.details(), draft.requestHash(), draft.expiresAt(), "COMPLETED",
                    result.id(), result.status().name(), outcome);
        });
    }

    private SupportConversationRepository.Row ownedConversation(UUID id, int actorId) {
        SupportConversationRepository.Row row = conversations.lock(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada."));
        if (row.ownerUserId() == null || row.ownerUserId() != actorId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada.");
        }
        return row;
    }

    private SupportDtos.VolunteerApplicationDraft ownedDraft(int actorId, UUID conversationId, UUID draftId) {
        SupportDtos.VolunteerApplicationDraft draft = drafts.lock(draftId)
                .or(() -> drafts.receipt(draftId, actorId, conversationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prévia não encontrada."));
        if ("COMPLETED".equals(draft.state())) return draft;
        if (!actorIdMatches(draft, actorId) || !conversationId.equals(conversationFor(draftId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prévia não encontrada.");
        }
        return draft;
    }

    private SupportDtos.VolunteerApplicationDraft ownedDraftForUpdate(int actorId, UUID conversationId, UUID draftId) {
        return ownedDraft(actorId, conversationId, draftId);
    }

    private boolean actorIdMatches(SupportDtos.VolunteerApplicationDraft draft, int actorId) {
        return drafts.belongsTo(draft.id(), actorId);
    }

    private UUID conversationFor(UUID draftId) { return drafts.conversationId(draftId); }

    private static void verifyHash(SupportDtos.VolunteerApplicationDraft draft, String expectedHash) {
        if (expectedHash == null || !MessageDigest.isEqual(draft.requestHash().getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A prévia foi alterada. Solicite uma nova confirmação.");
        }
    }

    private static String bindingHash(int actorId, UUID conversationId, int opportunityId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    ("VOLUNTEER_APPLICATION|" + actorId + "|" + conversationId + "|" + opportunityId + "|{}").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception impossible) { throw new IllegalStateException("SHA-256 indisponível", impossible); }
    }

    private static String successMessage(String status) {
        return switch (status == null ? "" : status) {
            case "PENDENTE" -> "Sua candidatura foi enviada! Agora ela está aguardando a análise da ONG.";
            case "APROVADA" -> "Sua participação foi registrada como aprovada pela ONG.";
            default -> "A candidatura foi registrada com o status " + (status == null ? "atualizado" : status) + ".";
        };
    }

    private static SupportDtos.VolunteerApplicationDraft withMessage(SupportDtos.VolunteerApplicationDraft draft, String message) {
        return new SupportDtos.VolunteerApplicationDraft(draft.id(), draft.opportunityId(), draft.title(), draft.ngoName(),
                draft.details(), draft.requestHash(), draft.expiresAt(), draft.state(), draft.applicationId(),
                draft.applicationStatus(), message);
    }

    private static String ngoName(String summary) {
        String prefix = "Oportunidade de ";
        if (summary == null || !summary.startsWith(prefix)) return "ONG participante";
        int end = summary.indexOf(". ", prefix.length());
        return end < 0 ? bounded(summary.substring(prefix.length()), 255)
                : bounded(summary.substring(prefix.length(), end), 255);
    }

    private static String bounded(String value, int max) {
        String safe = value == null ? "" : value.strip().replaceAll("[\\p{Cntrl}]", " ");
        return safe.substring(0, Math.min(safe.length(), max));
    }

    private static int requireUser(Usuario user) {
        if (user == null || user.getId() == null || !user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre na sua conta para continuar.");
        }
        return user.getId();
    }

    private static void requireAiMayAct(SupportConversationRepository.Row conversation) {
        if (!"AI_ACTIVE".equals(conversation.state())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A Sophie não pode confirmar ações enquanto a equipe humana estiver com esta conversa.");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
