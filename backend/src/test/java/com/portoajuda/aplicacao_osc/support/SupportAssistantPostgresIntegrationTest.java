package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAssignment;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentStatus;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.admin.AdminProfile;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.GeminiRequestBudget;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in only for a disposable loopback PostgreSQL database with M15 applied. */
class SupportAssistantPostgresIntegrationTest {
    private String url;
    private String user;
    private String password;
    private int userId;
    private int staffId;
    private UUID conversationId;
    private SupportConversationRepository conversations;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private final List<Integer> extraStaffIds = new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("SUPPORT_ASSISTANT_TEST_CONFIRMED")),
                "Explicit disposable PostgreSQL confirmation is required.");
        url=System.getenv("SUPPORT_ASSISTANT_TEST_JDBC_URL");
        user=System.getenv("SUPPORT_ASSISTANT_TEST_DB_USER");
        password=System.getenv("SUPPORT_ASSISTANT_TEST_DB_PASSWORD");
        Assumptions.assumeTrue(url!=null && url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55[0-9]{3}/porto_support_(review2|review3|retention_test|retention_final|retention_final2)(?:\\?sslmode=disable)?")
                && user!=null && !user.isBlank() && password!=null && !password.isBlank(),
                "Only the explicitly named loopback disposable database is accepted.");
        var source=new DriverManagerDataSource(url,user,password);
        jdbc=new JdbcTemplate(source);
        ensureTestAdminDirectorySchema();
        conversations=new SupportConversationRepository(jdbc);
        transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
        userId=900_000_000+(int)(Math.random()*90_000_000);
        jdbc.update("INSERT INTO public.usuarios(id) VALUES (?)",userId);
        staffId=userId+1;
        jdbc.update("INSERT INTO public.usuarios(id) VALUES (?)",staffId);
        conversationId=UUID.randomUUID();
        jdbc.update("INSERT INTO public.support_conversations(id,owner_user_id,state) VALUES (?,?,'WAITING_HUMAN')",conversationId,userId);
    }

    @AfterEach void cleanSyntheticAccountOnly() {
        if(jdbc!=null && userId>0) {
            for (Integer id : extraStaffIds) jdbc.update("DELETE FROM public.admin_assignments WHERE usuario_id=?", id);
            for (Integer id : extraStaffIds) jdbc.update("DELETE FROM public.usuario_roles WHERE id_usuario=?", id);
            for (Integer id : extraStaffIds) jdbc.update("DELETE FROM public.usuarios WHERE id=?", id);
            jdbc.update("DELETE FROM public.usuarios WHERE id IN (?,?)",userId,staffId);
        }
        // Conversation, messages and events intentionally remain as immutable synthetic test history.
    }

    @Test void concurrentStaffClaimsAllowExactlyOneOwner() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(3);
        try {
            Future<Integer> first=pool.submit(()->claim(910001,barrier));
            Future<Integer> second=pool.submit(()->claim(910002,barrier));
            barrier.await();
            assertEquals(1,first.get()+second.get());
            var finalRow=conversations.find(conversationId).orElseThrow();
            assertEquals("HUMAN_ACTIVE",finalRow.state());
            assertTrue(finalRow.assignedStaffUserId()==910001||finalRow.assignedStaffUserId()==910002);
        } finally { pool.shutdownNow(); }
    }

    @Test void returningFromActiveHumanSupportPersistsStaffNotificationInWaitingQueue() {
        var authorization=mock(AdminAuthorizationService.class);
        when(authorization.requirePermission(AdminPermission.SUPPORT_MANAGE))
                .thenReturn(new AdminActor(staffId,UUID.randomUUID(),AdminProfile.MODERATOR));
        var provider=mock(SupportAssistantProvider.class);
        var service=serviceWithProvider(true,provider,authorization);
        var user=user(userId);
        assertEquals("HUMAN_ACTIVE",service.takeOver(conversationId).state());

        var returned=service.continueWithSophie(conversationId,user);

        assertEquals("AI_ACTIVE",returned.state());
        assertTrue(returned.messages().stream().anyMatch(message->message.senderType().equals("USER")
                && message.body().contains("continuar esta conversa com a Sophie")));
        SupportDtos.ConversationSummary staffNotice=null;
        for(int page=0;page<100&&staffNotice==null;page++){
            staffNotice=conversations.listStaff("WAITING_HUMAN",50,page*50).stream()
                    .filter(item->item.id().equals(conversationId)).findFirst().orElse(null);
        }
        assertTrue(staffNotice!=null);
        assertEquals("AI_ACTIVE",staffNotice.state());
        assertTrue(staffNotice.unread());
        verifyNoInteractions(provider);
    }

    @Test void concurrentUserReturnAndStaffTakeoverDoNotLeaveHumanAssignmentOrMixedReplies() throws Exception {
        var authorization=mock(AdminAuthorizationService.class);
        when(authorization.requirePermission(AdminPermission.SUPPORT_MANAGE))
                .thenReturn(new AdminActor(staffId,UUID.randomUUID(),AdminProfile.MODERATOR));
        var provider=mock(SupportAssistantProvider.class);
        var service=serviceWithProvider(true,provider,authorization);
        Usuario owner=user(userId);
        var start=new CyclicBarrier(3);
        var pool=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> userReturn=pool.submit(()->{
                start.await();
                try { service.continueWithSophie(conversationId,owner); return true; }
                catch(org.springframework.web.server.ResponseStatusException conflict) {
                    if(conflict.getStatusCode().value()==409) return false;
                    throw conflict;
                }
            });
            Future<Boolean> staffClaim=pool.submit(()->{
                start.await();
                try { service.takeOver(conversationId); return true; }
                catch(org.springframework.web.server.ResponseStatusException conflict) {
                    if(conflict.getStatusCode().value()==409) return false;
                    throw conflict;
                }
            });
            start.await();
            userReturn.get(); staffClaim.get();
            var finalRow=conversations.find(conversationId).orElseThrow();
            assertTrue(List.of("AI_ACTIVE","HUMAN_ACTIVE").contains(finalRow.state()),
                    "The final revision must represent either the user return or a subsequent authorized takeover.");
            if("AI_ACTIVE".equals(finalRow.state())) assertNull(finalRow.assignedStaffUserId());
            else assertEquals(staffId,finalRow.assignedStaffUserId());
            assertFalse(conversations.messages(conversationId,50).stream().anyMatch(message->message.senderType().equals("AI")));
            assertTrue(conversations.transitions(conversationId).stream()
                    .anyMatch(event->"AI_ACTIVE".equals(event.toState())&&"USER".equals(event.actorType())));
            verifyNoInteractions(provider);
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentNewConversationRequestClosesPendingRequestAndCreatesOneHistoryRecord() throws Exception {
        var service = service(false);
        Usuario owner = user(userId);
        UUID requestedId = UUID.randomUUID();
        var request = new SupportDtos.NewConversationRequest(requestedId, conversationId);
        var start = new CyclicBarrier(3);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = pool.submit(() -> {
                start.await();
                return service.startNewConversation(request, owner).id();
            });
            Future<UUID> duplicate = pool.submit(() -> {
                start.await();
                return service.startNewConversation(request, owner).id();
            });
            start.await();
            assertEquals(requestedId, first.get());
            assertEquals(requestedId, duplicate.get());
            assertEquals("CLOSED", conversations.find(conversationId).orElseThrow().state());
            assertEquals("AI_ACTIVE", conversations.find(requestedId).orElseThrow().state());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.support_conversations WHERE id=? AND owner_user_id=?", Integer.class, requestedId, userId));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.support_conversations WHERE owner_user_id=? AND state<>'CLOSED'", Integer.class, userId));
            assertTrue(conversations.messages(conversationId, 20).stream().anyMatch(message -> message.body().contains("histórico permanece disponível")));
            assertEquals(1, conversations.messages(requestedId, 20).stream().filter(message -> message.body().equals(
                    "Olá! Sou a Sophie, assistente virtual do Porto Ajuda. Como posso ajudar?")).count());
            assertEquals(2, service.mine(owner).size(), "The closed conversation remains in the user's history.");
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentTransfersToDifferentEligibleRecipientsKeepOneAtomicAssignee() throws Exception {
        int firstRecipient = seedSupportManager(AdminProfile.MODERATOR);
        int secondRecipient = seedSupportManager(AdminProfile.PLATFORM_ADMIN);
        AdminAssignmentRepository assignmentRepository = new AdminAssignmentRepository(jdbc);
        var firstService = transferService(assignmentRepository, transferAuthorization(firstRecipient, secondRecipient));
        var secondService = transferService(assignmentRepository, transferAuthorization(firstRecipient, secondRecipient));
        UUID activeId = UUID.randomUUID();
        jdbc.update("INSERT INTO public.support_conversations(id,owner_user_id,state,assigned_staff_user_id) VALUES (?,?,'HUMAN_ACTIVE',?)",
                activeId, userId, staffId);
        var start = new CyclicBarrier(3);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> transferAfterBarrier(firstService, activeId, firstRecipient, start));
            Future<Boolean> second = pool.submit(() -> transferAfterBarrier(secondService, activeId, secondRecipient, start));
            start.await();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            Integer assignee = jdbc.queryForObject("SELECT assigned_staff_user_id FROM public.support_conversations WHERE id=?", Integer.class, activeId);
            assertTrue(assignee.equals(firstRecipient) || assignee.equals(secondRecipient));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.support_conversation_events WHERE conversation_id=? AND event_type='TRANSFERRED'", Integer.class, activeId));
        } finally { pool.shutdownNow(); }
    }

    private int claim(int staffId,CyclicBarrier barrier) throws Exception {
        barrier.await();
        return transactions.execute(status->{
            var row=conversations.lock(conversationId).orElseThrow();
            if(!"WAITING_HUMAN".equals(row.state())) return 0;
            return conversations.transition(conversationId,row.version(),"WAITING_HUMAN","HUMAN_ACTIVE",staffId,Instant.now());
        });
    }

    @Test void concurrentDuplicateMessagesCreateExactlyOneDurableRow() throws Exception {
        String key=UUID.randomUUID().toString(); var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(3);
        try {
            Future<Long> first=pool.submit(()->insertDuplicate(key,barrier,"first"));
            Future<Long> second=pool.submit(()->insertDuplicate(key,barrier,"second"));
            barrier.await();
            assertEquals(first.get(),second.get());
            Integer count=jdbc.queryForObject("SELECT count(*) FROM public.support_messages WHERE conversation_id=? AND idempotency_key=?",Integer.class,conversationId,key);
            assertEquals(1,count);
        } finally { pool.shutdownNow(); }
    }

    private long insertDuplicate(String key,CyclicBarrier barrier,String body) throws Exception {
        barrier.await();
        return transactions.execute(status->conversations.addMessage(conversationId,"USER",userId,"You",body,key));
    }

    private int seedSupportManager(AdminProfile profile) {
        int id = userId + 1_000 + (int)(Math.random() * 1_000);
        jdbc.update("INSERT INTO public.usuarios(id) VALUES (?)", id);
        jdbc.update("UPDATE public.usuarios SET nome=? WHERE id=?", "Synthetic Support Staff " + id, id);
        Integer adminRole = jdbc.queryForObject("SELECT id FROM public.roles WHERE nome='ADMIN' ORDER BY id LIMIT 1", Integer.class);
        jdbc.update("INSERT INTO public.usuario_roles(id_usuario,id_role) VALUES (?,?) ON CONFLICT DO NOTHING", id, adminRole);
        Instant now = Instant.now();
        new AdminAssignmentRepository(jdbc).insert(new AdminAssignment(UUID.randomUUID(), id, id, profile,
                AdminAssignmentStatus.ACTIVE, now, staffId, now, staffId, null, "synthetic concurrent support transfer"));
        extraStaffIds.add(id);
        return id;
    }

    private AdminAuthorizationService transferAuthorization(int firstRecipient, int secondRecipient) {
        AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
        when(authorization.requirePermission(AdminPermission.SUPPORT_MANAGE))
                .thenReturn(new AdminActor(staffId, UUID.randomUUID(), AdminProfile.MODERATOR));
        when(authorization.userHasPermission(firstRecipient, AdminPermission.SUPPORT_MANAGE)).thenReturn(true);
        when(authorization.userHasPermission(secondRecipient, AdminPermission.SUPPORT_MANAGE)).thenReturn(true);
        return authorization;
    }

    private SupportAssistantService transferService(AdminAssignmentRepository repository, AdminAuthorizationService authorization) {
        var beans = new StaticListableBeanFactory();
        ObjectProvider<SupportAssistantProvider> providers = beans.getBeanProvider(SupportAssistantProvider.class);
        return new SupportAssistantService(conversations, mock(SupportPublicContentSearch.class), authorization,
                repository, providers, mock(GeminiRequestBudget.class), transactions.getTransactionManager(), true, false, 12);
    }

    private SupportAssistantService serviceWithProvider(boolean aiEnabled, SupportAssistantProvider provider,
            AdminAuthorizationService authorization) {
        var beans=new StaticListableBeanFactory(); beans.addBean("supportProvider",provider);
        ObjectProvider<SupportAssistantProvider> providers=beans.getBeanProvider(SupportAssistantProvider.class);
        return new SupportAssistantService(conversations,mock(SupportPublicContentSearch.class),authorization,
                new AdminAssignmentRepository(jdbc),providers,mock(GeminiRequestBudget.class),
                transactions.getTransactionManager(),true,aiEnabled,12);
    }

    private boolean transferAfterBarrier(SupportAssistantService service, UUID id, int target, CyclicBarrier start) throws Exception {
        start.await();
        try {
            service.transfer(id, new SupportDtos.TransferRequest(target, "Transferência concorrente sintética."));
            return true;
        } catch (org.springframework.web.server.ResponseStatusException denied) {
            if (denied.getStatusCode().value() == 403 || denied.getStatusCode().value() == 409) return false;
            throw denied;
        }
    }

    @Test void staleAiVersionCannotPersistAfterHumanStateTransition() {
        UUID aiConversation=UUID.randomUUID();
        jdbc.update("INSERT INTO public.support_conversations(id,owner_user_id,state) VALUES (?,?,'AI_ACTIVE')",aiConversation,userId);
        var before=conversations.find(aiConversation).orElseThrow();
        assertEquals(1,conversations.transition(aiConversation,before.version(),"AI_ACTIVE","WAITING_HUMAN",null,Instant.now()));
        assertEquals(0,conversations.transition(aiConversation,before.version(),"AI_ACTIVE","AI_ACTIVE",null,Instant.now()),
                "A provider response with the old revision must be suppressed after takeover/escalation.");
    }

    @Test void oneConversationUpdateCannotRewriteOwnerOrAppendOnlyMessage() {
        conversations.addMessage(conversationId,"USER",userId,"You","synthetic body",UUID.randomUUID().toString());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update(
                "UPDATE public.support_conversations SET owner_user_id=? WHERE id=?",userId+1,conversationId));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update(
                "UPDATE public.support_messages SET body='rewritten' WHERE conversation_id=?",conversationId));
        assertTrue(Instant.now().isAfter(conversations.find(conversationId).orElseThrow().createdAt()));
    }

    @Test void retentionUsesInclusiveExactThirtyDayClosureBoundaryAndKeepsOnlyTransitionMetadata() {
        UUID closed=UUID.randomUUID();
        Instant closedAt=Instant.now().minus(Duration.ofDays(40)).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("""
                INSERT INTO public.support_conversations(id,owner_user_id,state,closed_at,state_changed_at)
                VALUES (?,?,'CLOSED',?,?)
                """,closed,userId,java.sql.Timestamp.from(closedAt),java.sql.Timestamp.from(closedAt));
        conversations.addMessage(closed,"USER",userId,"You","synthetic retention body",UUID.randomUUID().toString());
        conversations.assignmentEvent(closed,"STAFF",userId,"STATE_CHANGED","HUMAN_ACTIVE","CLOSED",
                "Synthetic private reason",userId,null);
        Instant expiry=closedAt.plus(Duration.ofDays(30));
        assertFalse(conversations.purgeExpiredContent(closed,expiry.minusNanos(1000)),
                "One microsecond before the deadline must retain conversation content.");
        assertEquals(1,conversations.countMessages(closed));
        assertTrue(conversations.purgeExpiredContent(closed,expiry),
                "At the exact 30-day boundary the content is eligible for disposal.");
        assertEquals(0,conversations.countMessages(closed));
        var retained=conversations.find(closed).orElseThrow();
        assertEquals("CLOSED",retained.state()); assertNull(retained.ownerUserId());
        assertEquals(expiry,retained.contentPurgedAt()); assertEquals(closedAt,retained.closedAt());
        var transition=conversations.transitions(closed).getFirst();
        assertEquals("STATE_CHANGED",transition.eventType());
        assertEquals("HUMAN_ACTIVE",transition.fromState()); assertEquals("CLOSED",transition.toState());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM public.support_conversation_events WHERE conversation_id=? AND actor_user_id IS NULL AND reason IS NULL AND previous_assignee_user_id_snapshot IS NULL AND new_assignee_user_id_snapshot IS NULL",Integer.class,closed));
        assertNull(jdbc.queryForObject("SELECT last_message_at FROM public.support_conversations WHERE id=?",java.sql.Timestamp.class,closed));
    }

    @Test void reopeningCancelsDeadlineAndASecondClosureStartsANewThirtyDayPeriod() {
        UUID id=UUID.randomUUID();
        Instant firstClose=Instant.now().minus(Duration.ofDays(40)).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO public.support_conversations(id,owner_user_id,state,closed_at,state_changed_at) VALUES (?,?,'CLOSED',?,?)",
                id,userId,java.sql.Timestamp.from(firstClose),java.sql.Timestamp.from(firstClose));
        conversations.addMessage(id,"USER",userId,"You","synthetic body",UUID.randomUUID().toString());
        var closed=conversations.find(id).orElseThrow();
        Instant reopened=firstClose.plus(Duration.ofDays(5));
        assertEquals(1,conversations.transition(id,closed.version(),"CLOSED","AI_ACTIVE",null,reopened));
        assertNull(conversations.find(id).orElseThrow().closedAt());
        Instant originalDeadline=firstClose.plus(Duration.ofDays(30));
        assertFalse(conversations.purgeExpiredContent(id,originalDeadline),"Reopening cancels the earlier deadline.");
        var active=conversations.find(id).orElseThrow();
        Instant secondClose=reopened.plus(Duration.ofDays(1));
        assertEquals(1,conversations.transition(id,active.version(),"AI_ACTIVE","CLOSED",null,secondClose));
        Instant renewedDeadline=secondClose.plus(Duration.ofDays(30));
        assertFalse(conversations.purgeExpiredContent(id,originalDeadline),"The new closure receives a fresh 30-day period.");
        assertTrue(conversations.purgeExpiredContent(id,renewedDeadline));
        assertEquals(0,conversations.countMessages(id));
    }

    @Test void anOldWaitingConversationIsNotPurgedByAgeAlone() {
        UUID id=UUID.randomUUID();
        Instant old=Instant.now().minus(Duration.ofDays(500)).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO public.support_conversations(id,owner_user_id,state,created_at,updated_at,state_changed_at) VALUES (?,?,'WAITING_HUMAN',?,?,?)",
                id,userId,java.sql.Timestamp.from(old),java.sql.Timestamp.from(old),java.sql.Timestamp.from(old));
        assertFalse(conversations.purgeExpiredContent(id,Instant.now()));
        assertEquals("WAITING_HUMAN",conversations.find(id).orElseThrow().state());
        assertNull(conversations.find(id).orElseThrow().contentPurgedAt());
    }

    @Test void concurrentReopenAndDuePurgeCannotLeavePurgedConversationActiveOrLoseUnexpiredContent() throws Exception {
        UUID id=UUID.randomUUID();
        Instant closedAt=Instant.now().minus(Duration.ofDays(40)).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO public.support_conversations(id,owner_user_id,state,closed_at,state_changed_at) VALUES (?,?,'CLOSED',?,?)",
                id,userId,java.sql.Timestamp.from(closedAt),java.sql.Timestamp.from(closedAt));
        conversations.addMessage(id,"USER",userId,"You","synthetic race body",UUID.randomUUID().toString());
        var initial=conversations.find(id).orElseThrow();
        var pool=Executors.newFixedThreadPool(2); var start=new CyclicBarrier(3);
        Instant now=Instant.now();
        try {
            Future<Integer> reopen=pool.submit(()->{start.await();return transactions.execute(tx->conversations.transition(
                    id,initial.version(),"CLOSED","AI_ACTIVE",null,now));});
            Future<Boolean> purge=pool.submit(()->{start.await();return transactions.execute(tx->conversations.purgeExpiredContent(id,now));});
            start.await();
            int reopened=reopen.get(); boolean purged=purge.get();
            var result=conversations.find(id).orElseThrow();
            if(reopened==1){
                assertFalse(purged); assertEquals("AI_ACTIVE",result.state()); assertNull(result.closedAt());
                assertEquals(1,conversations.countMessages(id));
            }else{
                assertTrue(purged); assertEquals("CLOSED",result.state()); assertNotNull(result.contentPurgedAt());
                assertEquals(0,conversations.countMessages(id));
            }
        } finally {pool.shutdownNow();}
    }

    @Test void persistedUserToHumanWorkflowSurvivesPollingReloadAndClosure() {
        AdminAuthorizationService authorization=mock(AdminAuthorizationService.class);
        when(authorization.requirePermission(AdminPermission.SUPPORT_MANAGE))
                .thenReturn(new AdminActor(staffId,UUID.randomUUID(),AdminProfile.MODERATOR));
        when(authorization.userHasPermission(staffId,AdminPermission.SUPPORT_MANAGE)).thenReturn(true);
        var beans=new StaticListableBeanFactory();
        ObjectProvider<SupportAssistantProvider> providers=beans.getBeanProvider(SupportAssistantProvider.class);
        var service=new SupportAssistantService(conversations,
                mock(SupportPublicContentSearch.class),authorization,mock(AdminAssignmentRepository.class),
                providers,mock(GeminiRequestBudget.class),transactions.getTransactionManager(),true,false,12);
        Usuario user=new Usuario(); ReflectionTestUtils.setField(user,"id",userId);

        var created=service.create(user);
        assertEquals("AI_ACTIVE",created.state());
        String key=UUID.randomUUID().toString();
        var waiting=service.send(created.id(),user,new SupportDtos.UserMessageRequest("Preciso de orientação.",key));
        assertEquals("WAITING_HUMAN",waiting.state());
        assertTrue(waiting.messages().stream().anyMatch(m->m.senderType().equals("USER")&&m.body().equals("Preciso de orientação.")));
        assertTrue(waiting.messages().stream().anyMatch(m->m.senderType().equals("SYSTEM")&&m.body().contains("equipe")));
        var historySummary=service.mine(user).getFirst();
        assertEquals(waiting.id(),historySummary.id());
        assertEquals("Preciso de orientação.",historySummary.firstUserMessage());
        assertTrue(historySummary.lastMessagePreview().contains("encaminhada"));

        var claimed=service.takeOver(created.id());
        assertEquals("HUMAN_ACTIVE",claimed.state());
        assertEquals("Equipe Porto Ajuda",claimed.assignedStaffLabel());
        var replied=service.staffReply(created.id(),new SupportDtos.ReplyRequest("Como posso ajudar?",UUID.randomUUID().toString()));
        assertTrue(replied.messages().stream().anyMatch(m->m.senderType().equals("STAFF")&&m.body().equals("Como posso ajudar?")));
        var reloaded=service.mine(created.id(),user);
        assertEquals("HUMAN_ACTIVE",reloaded.state());
        assertTrue(reloaded.messages().stream().anyMatch(m->m.body().equals("Como posso ajudar?")));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->service.mine(created.id(),user(userId+10)));
        var closed=service.closeByStaff(created.id(),new SupportDtos.ActionRequest("Atendimento concluído."));
        assertEquals("CLOSED",closed.state());
        assertNotNull(closed.closedAt());
        assertEquals(closed.closedAt().plus(Duration.ofDays(30)),closed.contentExpiresAt());
        assertEquals("CLOSED",service.mine(created.id(),user).state());
    }

    private static Usuario user(int id) { Usuario user=new Usuario(); ReflectionTestUtils.setField(user,"id",id); return user; }

    private SupportAssistantService service(boolean aiEnabled) {
        var beans = new StaticListableBeanFactory();
        ObjectProvider<SupportAssistantProvider> providers = beans.getBeanProvider(SupportAssistantProvider.class);
        return new SupportAssistantService(conversations, mock(SupportPublicContentSearch.class),
                mock(AdminAuthorizationService.class), mock(AdminAssignmentRepository.class), providers,
                mock(GeminiRequestBudget.class), transactions.getTransactionManager(), true, aiEnabled, 12);
    }

    private void ensureTestAdminDirectorySchema() {
        jdbc.execute("ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS nome VARCHAR(255)");
        jdbc.execute("ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS nome_social VARCHAR(255)");
        jdbc.execute("ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS ativo BOOLEAN NOT NULL DEFAULT TRUE");
        jdbc.execute("CREATE TABLE IF NOT EXISTS public.roles (id SERIAL PRIMARY KEY, nome VARCHAR(64) NOT NULL UNIQUE)");
        jdbc.update("INSERT INTO public.roles(nome) VALUES ('ADMIN') ON CONFLICT (nome) DO NOTHING");
        jdbc.execute("CREATE TABLE IF NOT EXISTS public.usuario_roles (id_usuario INTEGER NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE, id_role INTEGER NOT NULL REFERENCES public.roles(id) ON DELETE CASCADE, PRIMARY KEY(id_usuario,id_role))");
    }
}
