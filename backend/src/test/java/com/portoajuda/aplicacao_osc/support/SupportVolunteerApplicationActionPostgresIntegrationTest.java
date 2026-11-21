package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.portoajuda.aplicacao_osc.dto.request.RequestCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.response.CandidaturaUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.DisponibilidadeCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.response.SituacaoMinhaCandidaturaDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import com.portoajuda.aplicacao_osc.service.CandidaturaVoluntariadoService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Opt-in integration coverage using a disposable loopback PostgreSQL database with M15 and M22 applied.
 * The action service/repositories/transaction manager here are real Spring/JDBC components; only the
 * existing domain candidaturas service is replaced with a transaction-participating synthetic fixture.
 */
class SupportVolunteerApplicationActionPostgresIntegrationTest {
    private DataSource source;
    private JdbcTemplate jdbc;
    private PlatformTransactionManager tx;
    private SupportConversationRepository conversations;
    private SupportVolunteerApplicationDraftRepository drafts;
    private SupportPublicContentSearch publicSearch;
    private CandidaturaVoluntariadoService applications;
    private AnnotationConfigApplicationContext spring;
    private SupportVolunteerApplicationActionService service;
    private MutableClock clock;
    private final AtomicBoolean failAfterApplicationInsert = new AtomicBoolean();

    @BeforeEach void connectOnlyToTheExplicitDisposableDatabase() {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("SUPPORT_ASSISTANT_DRAFT_PG_CONFIRMED")),
                "Explicit disposable database opt-in is required.");
        String url = System.getenv("SUPPORT_ASSISTANT_DRAFT_PG_URL");
        String user = System.getenv("SUPPORT_ASSISTANT_DRAFT_PG_USER");
        String password = System.getenv("SUPPORT_ASSISTANT_DRAFT_PG_PASSWORD");
        Assumptions.assumeTrue(url != null && url.matches(
                "jdbc:postgresql://127\\.0\\.0\\.1:55[0-9]{3}/porto_support_action_test(?:\\?sslmode=disable)?")
                && "postgres".equals(user),
                "Only the named loopback test database and postgres role are accepted.");
        source = new DriverManagerDataSource(url, user, password == null ? "" : password);
        jdbc = new JdbcTemplate(source);
        assertEquals("postgres", jdbc.queryForObject("SELECT current_user", String.class));
        assertEquals("postgres", jdbc.queryForObject("SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='public.support_volunteer_application_drafts'::regclass", String.class));
        assertTrue(jdbc.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid='public.support_volunteer_application_drafts'::regclass", Boolean.class));
        assertTrue(hasPrivilege("postgres", "support_volunteer_application_drafts", "SELECT"));
        assertTrue(hasPrivilege("postgres", "support_volunteer_application_drafts", "INSERT"));
        assertTrue(hasPrivilege("postgres", "support_volunteer_application_drafts", "UPDATE"));
        assertTrue(hasPrivilege("postgres", "support_volunteer_application_drafts", "DELETE"));
        for (String role : List.of("anon", "authenticated")) {
            for (String table : List.of("support_volunteer_application_drafts", "support_volunteer_application_receipts")) {
                for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE"))
                    assertFalse(hasPrivilege(role, table, privilege), role + " must not have " + privilege + " on " + table);
            }
        }
        tx = new DataSourceTransactionManager(source);
        conversations = new SupportConversationRepository(jdbc);
        drafts = new SupportVolunteerApplicationDraftRepository(jdbc);
        publicSearch = mock(SupportPublicContentSearch.class);
        applications = mock(CandidaturaVoluntariadoService.class);
        clock = new MutableClock(Instant.now().minusSeconds(2));
        failAfterApplicationInsert.set(false);

        when(publicSearch.publicVolunteerOpportunity(anyInt())).thenAnswer(call -> opportunity(call.getArgument(0)));
        when(applications.disponibilidadePublica(anyInt())).thenReturn(new DisponibilidadeCandidaturaDTO(true));
        when(applications.situacaoMinhaCandidatura(anyInt(), any())).thenAnswer(call -> {
            Usuario actor = call.getArgument(1);
            Integer count = jdbc.queryForObject("SELECT count(*) FROM public.support_test_applications WHERE actor_id=? AND opportunity_id=?",
                    Integer.class, actor.getId(), call.<Integer>getArgument(0));
            return new SituacaoMinhaCandidaturaDTO(count != null && count > 0,
                    count != null && count > 0 ? StatusCandidatura.PENDENTE : null);
        });
        when(applications.candidatar(anyInt(), any(), any())).thenAnswer(call -> {
            int opportunityId = call.getArgument(0);
            Usuario actor = call.getArgument(2);
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(791231,?)", Object.class, opportunityId);
            Integer count = jdbc.queryForObject("SELECT count(*) FROM public.support_test_applications WHERE actor_id=? AND opportunity_id=?",
                    Integer.class, actor.getId(), opportunityId);
            if (count != null && count > 0) throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Synthetic domain duplicate prevention.");
            Integer applicationId = jdbc.queryForObject("INSERT INTO public.support_test_applications(actor_id,opportunity_id) VALUES (?,?) RETURNING id",
                    Integer.class, actor.getId(), opportunityId);
            if (failAfterApplicationInsert.get()) throw new IllegalStateException("Synthetic failure after application insert.");
            return application(applicationId, opportunityId, actor.getId());
        });

        spring = new AnnotationConfigApplicationContext();
        spring.registerBean(DataSource.class, () -> source);
        spring.registerBean(JdbcTemplate.class, () -> jdbc);
        spring.registerBean(PlatformTransactionManager.class, () -> tx);
        spring.registerBean(SupportConversationRepository.class, () -> conversations);
        spring.registerBean(SupportVolunteerApplicationDraftRepository.class, () -> drafts);
        spring.registerBean(SupportPublicContentSearch.class, () -> publicSearch);
        spring.registerBean(CandidaturaVoluntariadoService.class, () -> applications);
        spring.registerBean(SupportVolunteerApplicationActionService.class,
                () -> new SupportVolunteerApplicationActionService(drafts, conversations, publicSearch, applications, tx, clock));
        spring.refresh();
        service = spring.getBean(SupportVolunteerApplicationActionService.class);
        jdbc.execute("DROP TABLE IF EXISTS public.support_test_applications");
        jdbc.execute("CREATE TABLE public.support_test_applications(id integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, actor_id integer NOT NULL, opportunity_id integer NOT NULL, UNIQUE(actor_id,opportunity_id))");
    }

    @AfterEach void closeSpringContext() { if (spring != null) spring.close(); }

    @Test void expiredDraftIsAtomicallyReplacedForTheSameConversationAndOpportunity() {
        Usuario actor = createUser();
        UUID conversation = createConversation(actor);
        var first = service.createDraft(actor, conversation, 71, "", "");
        clock.advanceSeconds(301);

        var second = service.createDraft(actor, conversation, 71, "", "");

        assertNotEquals(first.id(), second.id());
        assertEquals("EXPIRED", state(first.id()));
        assertEquals("DRAFT", state(second.id()));
        assertEquals(1, drafts.openFor(actor.getId(), conversation, clock.instant()).size());
    }

    @Test void abandonedPreviewAndExpiredReceiptCleanupDoesNotDependOnConversationMessages() {
        Usuario actor = createUser();
        UUID emptyConversation = createConversation(actor);
        insertDraft(actor.getId(), emptyConversation, 70, "DRAFT");
        UUID conversation = createConversation(actor);
        var current = service.createDraft(actor, conversation, 71, "", "");
        service.confirm(actor, conversation, current.id(), current.requestHash());

        clock.advanceSeconds(31L * 86400);
        assertTrue(new SupportVolunteerActionRetentionJob(drafts, clock).purgeExpiredActionData() >= 2);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.support_volunteer_application_drafts WHERE actor_user_id=?", Integer.class, actor.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.support_volunteer_application_receipts WHERE actor_user_id=?", Integer.class, actor.getId()));
        assertEquals(0, conversations.countMessages(emptyConversation));
    }

    @Test void confirmationAcquiringTheDraftLockAfterExpiryDoesNotCallTheApplicationService() throws Exception {
        Usuario actor = createUser();
        UUID conversation = createConversation(actor);
        var draft = service.createDraft(actor, conversation, 71, "", "");
        clock.advanceSeconds(299);

        try (Connection locker = source.getConnection()) {
            locker.setAutoCommit(false);
            try (PreparedStatement lock = locker.prepareStatement(
                    "SELECT id FROM public.support_volunteer_application_drafts WHERE id=? FOR UPDATE")) {
                lock.setObject(1, draft.id());
                lock.executeQuery().close();
            }
            var started = new CountDownLatch(1);
            try (var pool = Executors.newSingleThreadExecutor()) {
                var confirmation = pool.submit(() -> { started.countDown(); return service.confirm(actor, conversation, draft.id(), draft.requestHash()); });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertTrue(waitUntilDraftLockIsBlocked());
                clock.advanceSeconds(2);
                locker.commit();
                var outcome = confirmation.get(10, TimeUnit.SECONDS);
                assertEquals("EXPIRED", outcome.state());
            }
        }
        verify(applications, never()).candidatar(anyInt(), any(RequestCandidaturaDTO.class), any());
        assertEquals("EXPIRED", state(draft.id()));
    }

    @Test void applicationInsertAndDraftCompletionCommitOrRollbackTogether() {
        Usuario actor = createUser();
        UUID conversation = createConversation(actor);
        var draft = service.createDraft(actor, conversation, 71, "", "");
        failAfterApplicationInsert.set(true);

        assertThrows(IllegalStateException.class,
                () -> service.confirm(actor, conversation, draft.id(), draft.requestHash()));

        assertEquals(0, applicationCount(actor.getId(), 71));
        assertEquals("DRAFT", state(draft.id()));
        failAfterApplicationInsert.set(false);
        var completed = service.confirm(actor, conversation, draft.id(), draft.requestHash());
        assertEquals("COMPLETED", completed.state());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.support_volunteer_application_drafts WHERE id=?", Integer.class, draft.id()));
        assertEquals("PENDENTE", jdbc.queryForObject("SELECT result_status FROM public.support_volunteer_application_receipts WHERE draft_id=?", String.class, draft.id()));
        assertEquals(1, applicationCount(actor.getId(), 71));
    }

    @Test void concurrentSameDraftAndDifferentConversationConfirmationsCannotDuplicateApplication() throws Exception {
        Usuario actor = createUser();
        UUID firstConversation = createConversation(actor);
        UUID secondConversation = createConversation(actor);
        var first = service.createDraft(actor, firstConversation, 71, "", "");
        var second = service.createDraft(actor, secondConversation, 71, "", "");
        try (var pool = Executors.newFixedThreadPool(4)) {
            var sameA = pool.submit(() -> service.confirm(actor, firstConversation, first.id(), first.requestHash()));
            var sameB = pool.submit(() -> service.confirm(actor, firstConversation, first.id(), first.requestHash()));
            assertEquals("COMPLETED", sameA.get(10, TimeUnit.SECONDS).state());
            assertEquals("COMPLETED", sameB.get(10, TimeUnit.SECONDS).state());
            assertEquals(1, applicationCount(actor.getId(), 71));

            Usuario secondActor = createUser();
            UUID thirdConversation = createConversation(secondActor);
            UUID fourthConversation = createConversation(secondActor);
            var third = service.createDraft(secondActor, thirdConversation, 72, "", "");
            var fourth = service.createDraft(secondActor, fourthConversation, 72, "", "");
            var crossA = pool.submit(() -> confirmOutcome(secondActor, thirdConversation, third.id(), third.requestHash()));
            var crossB = pool.submit(() -> confirmOutcome(secondActor, fourthConversation, fourth.id(), fourth.requestHash()));
            List<String> results = List.of(crossA.get(10, TimeUnit.SECONDS), crossB.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("COMPLETED"::equals).count());
            assertEquals(1, results.stream().filter("CONFLICT"::equals).count());
            assertEquals(1, applicationCount(secondActor.getId(), 72));
        }
    }

    @Test void retentionRemovesPreviewAndMinimalReceiptEvenWhenConversationHasNoMessages() {
        Usuario actor = createUser();
        UUID closedConversation = createConversation(actor);
        insertDraft(actor.getId(), closedConversation, 81, "DRAFT");
        insertDraft(actor.getId(), closedConversation, 82, "CANCELLED");
        insertDraft(actor.getId(), closedConversation, 83, "EXPIRED");
        var completed = service.createDraft(actor, closedConversation, 84, "", "");
        var done = service.confirm(actor, closedConversation, completed.id(), completed.requestHash());
        assertEquals("COMPLETED", done.state());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.support_volunteer_application_receipts WHERE draft_id=?", Integer.class, completed.id()));
        Instant closedAt = clock.instant().minusSeconds(31L * 86400);
        jdbc.update("UPDATE public.support_conversations SET state='CLOSED',closed_at=?,state_changed_at=? WHERE id=?",
                java.sql.Timestamp.from(closedAt), java.sql.Timestamp.from(closedAt), closedConversation);

        assertTrue(conversations.purgeExpiredContent(closedConversation, clock.instant()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.support_volunteer_application_drafts WHERE conversation_id=?", Integer.class, closedConversation));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.support_volunteer_application_receipts WHERE conversation_id=?", Integer.class, closedConversation));

        Usuario deleting = createUser();
        UUID openConversation = createConversation(deleting);
        insertDraft(deleting.getId(), openConversation, 85, "DRAFT");
        var accountReceipt = service.createDraft(deleting, openConversation, 86, "", "");
        service.confirm(deleting, openConversation, accountReceipt.id(), accountReceipt.requestHash());
        jdbc.update("DELETE FROM public.usuarios WHERE id=?", deleting.getId());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.support_volunteer_application_drafts WHERE actor_user_id=?", Integer.class, deleting.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.support_volunteer_application_receipts WHERE actor_user_id=?", Integer.class, deleting.getId()));
    }

    @Test void receiptMakesConfirmationRetryIdempotentUntilRetentionAndDomainCheckPreventsReplayAfterCleanup() {
        Usuario actor = createUser();
        UUID conversation = createConversation(actor);
        var draft = service.createDraft(actor, conversation, 91, "", "");
        var first = service.confirm(actor, conversation, draft.id(), draft.requestHash());
        var retry = service.confirm(actor, conversation, draft.id(), draft.requestHash());
        assertEquals(first.applicationId(), retry.applicationId());
        assertEquals(1, applicationCount(actor.getId(), 91));

        drafts.purgeExpiredReceipts(clock.instant().plusSeconds(31L * 86400));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.support_volunteer_application_receipts WHERE draft_id=?", Integer.class, draft.id()));
        assertThrows(ResponseStatusException.class, () -> service.createDraft(actor, createConversation(actor), 91, "", ""));
        assertEquals(1, applicationCount(actor.getId(), 91));
    }

    private Usuario createUser() {
        int id = 900_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(90_000_000);
        jdbc.update("INSERT INTO public.usuarios(id) VALUES (?)", id);
        Usuario actor = mock(Usuario.class);
        when(actor.getId()).thenReturn(id);
        when(actor.isEnabled()).thenReturn(true);
        return actor;
    }

    private UUID createConversation(Usuario actor) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.support_conversations(id,owner_user_id,state) VALUES (?,?,'AI_ACTIVE')", id, actor.getId());
        return id;
    }

    private UUID createClosedConversation(Usuario actor) {
        UUID id = UUID.randomUUID();
        Instant closed = clock.instant().minusSeconds(31L * 86400);
        jdbc.update("INSERT INTO public.support_conversations(id,owner_user_id,state,closed_at,state_changed_at) VALUES (?,?,'CLOSED',?,?)",
                id, actor.getId(), java.sql.Timestamp.from(closed), java.sql.Timestamp.from(closed));
        return id;
    }

    private void insertDraft(int actorId, UUID conversationId, int target, String state) {
        boolean completed = "COMPLETED".equals(state);
        jdbc.update("""
                INSERT INTO public.support_volunteer_application_drafts
                    (actor_user_id,conversation_id,target_opportunity_id,request_hash,opportunity_title,ngo_name,
                     public_details,state,result_application_id,result_status,completed_at,expires_at)
                VALUES (?,?,?,?,?,?,?, ?,?,?,?, clock_timestamp()+interval '4 minutes')
                """, actorId, conversationId, target, "a".repeat(64), "Synthetic", "Synthetic NGO", "Synthetic details",
                state, completed ? 90001 : null, completed ? "PENDENTE" : null, completed ? java.sql.Timestamp.from(clock.instant()) : null);
    }

    private int applicationCount(int actorId, int opportunityId) {
        return jdbc.queryForObject("SELECT count(*) FROM public.support_test_applications WHERE actor_id=? AND opportunity_id=?",
                Integer.class, actorId, opportunityId);
    }

    private boolean hasPrivilege(String role, String table, String privilege) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT has_table_privilege(?, ?, ?)", Boolean.class,
                role, "public." + table, privilege));
    }

    private String state(UUID draftId) {
        return jdbc.queryForObject("SELECT state FROM public.support_volunteer_application_drafts WHERE id=?", String.class, draftId);
    }

    private boolean waitUntilDraftLockIsBlocked() throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < until) {
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                     WHERE datname=current_database() AND wait_event_type='Lock'
                       AND query LIKE '%support_volunteer_application_drafts%'
                    """, Integer.class);
            if (waiting != null && waiting > 0) return true;
            Thread.sleep(25);
        }
        return false;
    }

    private String confirmOutcome(Usuario actor, UUID conversation, UUID draft, String hash) {
        try { return service.confirm(actor, conversation, draft, hash).state(); }
        catch (ResponseStatusException conflict) { return conflict.getStatusCode().value() == 409 ? "CONFLICT" : "HTTP_" + conflict.getStatusCode().value(); }
    }

    private SupportDtos.SearchResult opportunity(Integer id) {
        return new SupportDtos.SearchResult("volunteering:" + id, "VOLUNTARIADO", "Synthetic Opportunity",
                "Oportunidade de Synthetic NGO. Detalhes públicos sintéticos.", "/ong-profile?id=11#voluntariado");
    }

    private CandidaturaUsuarioDTO application(int id, int opportunityId, int actorId) {
        return new CandidaturaUsuarioDTO(id, opportunityId, 81001, 11, "Synthetic Opportunity", "Synthetic NGO",
                java.time.LocalDate.now(), StatusCandidatura.PENDENTE, null, null, "Santos", null, null, null, null,
                null, null, false, true);
    }

    private final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        synchronized void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public synchronized Instant instant() { return now; }
    }
}
