package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.portoajuda.aplicacao_osc.dto.response.CandidaturaUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.DisponibilidadeCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.response.SituacaoMinhaCandidaturaDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import com.portoajuda.aplicacao_osc.service.CandidaturaVoluntariadoService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

class SupportVolunteerApplicationActionServiceTest {
    private static final UUID CONVERSATION = UUID.fromString("12345678-1234-4234-8234-123456789012");
    private static final UUID DRAFT_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final String HASH = "1".repeat(64);
    private static final SupportDtos.SearchResult PUBLIC = new SupportDtos.SearchResult("volunteering:71",
            "VOLUNTARIADO", "Mutirão", "Oportunidade de Casa Exemplo. Local da atividade: Santos.", "/ong-profile?id=11#voluntariado");

    @Test void explicitConfirmationExecutesThroughExistingApplicationServiceAndRecordsTheOutcome() {
        Fixture f = new Fixture(Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneId.of("UTC")));
        var preview = f.service.createDraft(f.user, CONVERSATION, 71, "ignored title", "ignored summary");
        assertEquals("Mutirão", preview.title());
        assertEquals("Casa Exemplo", preview.ngoName());
        assertEquals("DRAFT", preview.state());
        verify(f.applications, never()).candidatar(anyInt(), any(), any());

        var result = f.service.confirm(f.user, CONVERSATION, DRAFT_ID, HASH);

        assertEquals("COMPLETED", result.state());
        assertEquals("PENDENTE", result.applicationStatus());
        assertEquals("Sua candidatura foi enviada! Agora ela está aguardando a análise da ONG.", result.message());
        verify(f.applications, times(1)).candidatar(eq(71), any(), eq(f.user));
    }

    @Test void cancelExpiresAndCrossUserAccessNeverExecuteTheApplication() {
        Fixture f = new Fixture(Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneId.of("UTC")));
        f.service.createDraft(f.user, CONVERSATION, 71, "", "");
        ResponseStatusException altered = assertThrows(ResponseStatusException.class,
                () -> f.service.confirm(f.user, CONVERSATION, DRAFT_ID, "2".repeat(64)));
        assertEquals(400, altered.getStatusCode().value());
        var cancelled = f.service.cancel(f.user, CONVERSATION, DRAFT_ID, HASH);
        assertEquals("CANCELLED", cancelled.state());
        verify(f.drafts).cancel(DRAFT_ID);
        var other = f.user(99);
        ResponseStatusException denial = assertThrows(ResponseStatusException.class,
                () -> f.service.confirm(other, CONVERSATION, DRAFT_ID, HASH));
        assertEquals(404, denial.getStatusCode().value());
        verify(f.applications, never()).candidatar(anyInt(), any(), any());
    }

    @Test void expiredOrChangedPublicEligibilityCannotReachTheApplicationService() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-19T12:00:00Z"));
        Fixture f = new Fixture(clock);
        f.service.createDraft(f.user, CONVERSATION, 71, "", "");
        clock.advanceSeconds(301);
        var expired = f.service.confirm(f.user, CONVERSATION, DRAFT_ID, HASH);
        assertEquals("EXPIRED", expired.state());
        verify(f.drafts).expire(DRAFT_ID);
        verify(f.applications, never()).candidatar(anyInt(), any(), any());

        Fixture changed = new Fixture(Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneId.of("UTC")));
        changed.service.createDraft(changed.user, CONVERSATION, 71, "", "");
        when(changed.publicSearch.publicVolunteerOpportunity(71)).thenReturn(null, null);
        assertThrows(ResponseStatusException.class, () -> changed.service.confirm(changed.user, CONVERSATION, DRAFT_ID, HASH));
        verify(changed.applications, never()).candidatar(anyInt(), any(), any());
    }

    @Test void fullCapacityAndHumanTakeoverBlockExecution() {
        Fixture full = new Fixture(Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneId.of("UTC")));
        when(full.applications.disponibilidadePublica(71)).thenReturn(new DisponibilidadeCandidaturaDTO(false));
        assertThrows(ResponseStatusException.class, () -> full.service.createDraft(full.user, CONVERSATION, 71, "", ""));
        verify(full.applications, never()).candidatar(anyInt(), any(), any());

        Fixture takeover = new Fixture(Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneId.of("UTC")));
        takeover.service.createDraft(takeover.user, CONVERSATION, 71, "", "");
        takeover.conversation = row("HUMAN_ACTIVE", 20);
        assertThrows(ResponseStatusException.class, () -> takeover.service.confirm(takeover.user, CONVERSATION, DRAFT_ID, HASH));
        verify(takeover.applications, never()).candidatar(anyInt(), any(), any());
    }

    @Test void concurrentAndRepeatedConfirmationsReturnOneRecordedApplication() throws Exception {
        Fixture f = new Fixture(Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneId.of("UTC")));
        f.service.createDraft(f.user, CONVERSATION, 71, "", "");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> f.service.confirm(f.user, CONVERSATION, DRAFT_ID, HASH));
            var second = executor.submit(() -> f.service.confirm(f.user, CONVERSATION, DRAFT_ID, HASH));
            assertEquals("COMPLETED", first.get().state());
            assertEquals("COMPLETED", second.get().state());
        }
        var retry = f.service.confirm(f.user, CONVERSATION, DRAFT_ID, HASH);
        assertEquals(902, retry.applicationId());
        verify(f.applications, times(1)).candidatar(eq(71), any(), eq(f.user));
    }

    private static final class Fixture {
        final SupportVolunteerApplicationDraftRepository drafts = spy(new MemoryDraftRepository());
        final SupportConversationRepository conversations = mock(SupportConversationRepository.class);
        final SupportPublicContentSearch publicSearch = mock(SupportPublicContentSearch.class);
        final CandidaturaVoluntariadoService applications = mock(CandidaturaVoluntariadoService.class);
        final Usuario user = user(20);
        final SupportVolunteerApplicationActionService service;
        SupportConversationRepository.Row conversation = row("AI_ACTIVE", 20);
        Fixture(Clock clock) {
            var lock = new ReentrantLock();
            PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
            when(tx.getTransaction(any(TransactionDefinition.class))).thenAnswer(call -> { lock.lock(); return new SimpleTransactionStatus(); });
            doAnswer(call -> { lock.unlock(); return null; }).when(tx).commit(any(TransactionStatus.class));
            doAnswer(call -> { lock.unlock(); return null; }).when(tx).rollback(any(TransactionStatus.class));
            when(conversations.lock(CONVERSATION)).thenAnswer(call -> Optional.of(conversation));
            when(publicSearch.publicVolunteerOpportunity(71)).thenReturn(PUBLIC);
            when(applications.disponibilidadePublica(71)).thenReturn(new DisponibilidadeCandidaturaDTO(true));
            when(applications.situacaoMinhaCandidatura(eq(71), any())).thenReturn(new SituacaoMinhaCandidaturaDTO(false, null));
            when(applications.candidatar(eq(71), any(), any())).thenReturn(new CandidaturaUsuarioDTO(902,71,81,11,
                    "Mutirão","Casa Exemplo",java.time.LocalDate.now(),StatusCandidatura.PENDENTE,null,null,"Santos",null,
                    null,null,null,null,null,false,true));
            service = new SupportVolunteerApplicationActionService(drafts, conversations, publicSearch,
                    applications, tx, clock);
        }
        Usuario user(int id) { Usuario user = mock(Usuario.class); when(user.getId()).thenReturn(id); when(user.isEnabled()).thenReturn(true); return user; }
    }

    private static SupportConversationRepository.Row row(String state, int owner) {
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        return new SupportConversationRepository.Row(CONVERSATION, owner, state, null, 2, now, now, now, now,
                null, null, false, false);
    }

    private static class MemoryDraftRepository extends SupportVolunteerApplicationDraftRepository {
        private SupportDtos.VolunteerApplicationDraft current;
        MemoryDraftRepository() { super(mock(JdbcTemplate.class)); }
        @Override public synchronized SupportDtos.VolunteerApplicationDraft createOrGet(UUID id, int actor, UUID conversation,
                int opportunity, String hash, String title, String ngo, String details, Instant expiresAt) {
            if (current == null || !"DRAFT".equals(current.state())) current = new SupportDtos.VolunteerApplicationDraft(
                    DRAFT_ID, opportunity, title, ngo, details, HASH, expiresAt, "DRAFT", null, null, null);
            return current;
        }
        @Override public synchronized List<SupportDtos.VolunteerApplicationDraft> openFor(int actor, UUID conversation, Instant now) {
            return current != null && "DRAFT".equals(current.state()) && now.isBefore(current.expiresAt()) ? List.of(current) : List.of();
        }
        @Override public synchronized Optional<SupportDtos.VolunteerApplicationDraft> lock(UUID id) { return Optional.ofNullable(current); }
        @Override public synchronized boolean belongsTo(UUID id, int actor) { return actor == 20; }
        @Override public synchronized UUID conversationId(UUID id) { return CONVERSATION; }
        @Override public synchronized int cancel(UUID id) { current = withState("CANCELLED", null, null); return 1; }
        @Override public synchronized int expire(UUID id) { current = withState("EXPIRED", null, null); return 1; }
        @Override public synchronized int complete(UUID id, Integer applicationId, String status, Instant at) {
            if ("COMPLETED".equals(current.state())) return 0;
            current = withState("COMPLETED", applicationId, status); return 1;
        }
        private SupportDtos.VolunteerApplicationDraft withState(String state, Integer applicationId, String status) {
            return new SupportDtos.VolunteerApplicationDraft(current.id(),current.opportunityId(),current.title(),current.ngoName(),
                    current.details(),current.requestHash(),current.expiresAt(),state,applicationId,status,null);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
