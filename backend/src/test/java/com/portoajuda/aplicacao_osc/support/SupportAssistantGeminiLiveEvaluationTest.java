package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.dto.response.OscListagemDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.service.GeminiRequestBudget;
import com.portoajuda.aplicacao_osc.service.OscService;
import com.portoajuda.aplicacao_osc.service.PostService;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;

/** Explicit synthetic-only Gemini evaluation; no existing account, conversation, or content is loaded. */
class SupportAssistantGeminiLiveEvaluationTest {
    private static final Duration LIVE_TIMEOUT = Duration.ofSeconds(45);
    private static final int SYNTHETIC_USER_ID = 987_654_321;
    private static final int SYNTHETIC_NGO_ID = 782_001;
    private static final String MODEL_DEFAULT = "gemini-3.1-flash-lite";
    private AtomicInteger providerRequests;
    private String apiKey;
    private String model;

    @BeforeEach void requireExplicitLiveOptInAndConfiguredCredential() {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("SUPPORT_ASSISTANT_LIVE_GEMINI_CONFIRMED")),
                "Explicit live-evaluation opt-in is required.");
        apiKey=System.getenv("GEMINI_API_KEY");
        model=nonblank(System.getenv("SUPPORT_ASSISTANT_AI_MODEL"),
                nonblank(System.getenv("NGO_VERIFICATION_AI_MODEL"),MODEL_DEFAULT));
        Assumptions.assumeTrue(apiKey!=null&&!apiKey.isBlank(),"Configured Gemini credential is unavailable.");
        providerRequests=new AtomicInteger();
    }

    @Test void evaluatesNaturalMultiTurnGuidanceFollowUpTopicChangeAndDeterministicHandoff() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        var builder=RestClient.builder();
        builder.messageConverters(converters->{converters.clear();converters.add(
                new JacksonJsonHttpMessageConverter(tools.jackson.databind.json.JsonMapper.builder().build()));});
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(LIVE_TIMEOUT).build());
        factory.setReadTimeout(LIVE_TIMEOUT);
        builder.requestFactory(factory).requestInterceptor((request,body,execution)->{
            if(providerRequests.incrementAndGet()>4) throw new IOException("Synthetic provider-call cap reached.");
            return execution.execute(request,body);
        });

        OscService ngos=mock(OscService.class);
        PostService posts=mock(PostService.class);
        var fictionalNgo=new OscListagemDTO(SYNTHETIC_NGO_ID,"ONG Arco Fictícia",null,CausaSocial.CULTURA,
                Set.of(),Set.of(CausaSocial.CULTURA),Set.of(),StatusOsc.ATIVA,
                "Mantém uma biblioteca comunitária fictícia com atividades de leitura.","Conteúdo público de avaliação sintético.",
                null,null,null,"Praia Grande","SP","Brasil",0,0);
        when(ngos.viewAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(fictionalNgo)));
        when(posts.listarPublicacoesCatalogo(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(posts.listarVoluntariadosCatalogo(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        var search=new SupportPublicContentSearch(ngos,posts);
        var budget=sharedEvaluationBudget();
        var provider=new GeminiSupportAssistantProvider(builder.build(),budget,mapper,apiKey,model,"Sophie",12000);
        var beans=new StaticListableBeanFactory(); beans.addBean("supportProvider",provider);
        ObjectProvider<SupportAssistantProvider> providers=beans.getBeanProvider(SupportAssistantProvider.class);
        var tx=mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any(TransactionDefinition.class))).thenAnswer(call->new SimpleTransactionStatus());
        doNothing().when(tx).commit(any(TransactionStatus.class));
        doNothing().when(tx).rollback(any(TransactionStatus.class));
        var repository=new InMemoryRepository();
        var service=new SupportAssistantService(repository,search,mock(AdminAuthorizationService.class),
                mock(AdminAssignmentRepository.class),providers,budget,tx,true,true,12,Clock.systemUTC(),"Sophie");
        Usuario user=new Usuario(); ReflectionTestUtils.setField(user,"id",SYNTHETIC_USER_ID);
        var conversation=service.create(user);
        System.out.printf("SUPPORT_GEMINI_LIVE model=%s timeoutSeconds=45 requestCap=4 sharedBudget=GeminiRequestBudget/test-process%n",model);

        var site=service.send(conversation.id(),user,new SupportDtos.UserMessageRequest(
                "Oi! Como encontro ONGs pelo site?",UUID.randomUUID().toString()));
        stopImmediatelyIfProviderFailed(site);
        assertTrue(site.messages().stream().anyMatch(message->message.senderType().equals("AI")
                && message.body().contains("/catalogo?categoria=ongs")));

        var followUp=service.send(conversation.id(),user,new SupportDtos.UserMessageRequest(
                "E existe alguma ONG de leitura perto de Praia Grande?",UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE",followUp.state(),"A contextual follow-up remains in the conversation.");
        assertTrue(followUp.messages().stream().anyMatch(message->message.senderType().equals("AI")
                && message.body().contains("/ong-profile?id="+SYNTHETIC_NGO_ID)));

        var topicChange=service.send(conversation.id(),user,new SupportDtos.UserMessageRequest(
                "Agora quero saber sobre voluntariado remoto.",UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE",topicChange.state(),"A new topic with no current results receives a useful next step.");
        assertEquals(3,topicChange.messages().stream().filter(message->message.senderType().equals("AI")).count(),
                "A topic change must have its own response and cannot reuse the previous turn's answer.");
        var lastAi=topicChange.messages().stream().filter(message->message.senderType().equals("AI"))
                .reduce((first,last)->last).orElseThrow();
        assertFalse(lastAi.body().isBlank());
        assertFalse(lastAi.body().contains("/ong-profile?id="+SYNTHETIC_NGO_ID),
                "The new topic must not reuse the prior topic's link as if it answered the current question.");

        var handoff=service.send(conversation.id(),user,new SupportDtos.UserMessageRequest(
                "Quero alterar minha senha.",UUID.randomUUID().toString()));
        assertEquals("WAITING_HUMAN",handoff.state());
        assertEquals(3,providerRequests.get(),"The private account action is routed without another provider request.");
        assertEquals(3,topicChange.messages().stream().filter(message->message.senderType().equals("AI")).count());
        System.out.printf("SUPPORT_GEMINI_LIVE requests=%d aiReplies=3 finalState=%s syntheticConversation=%s%n",
                providerRequests.get(),handoff.state(),conversation.id());
    }

    private void stopImmediatelyIfProviderFailed(SupportDtos.Conversation response) {
        if (response.messages().stream().anyMatch(message -> "AI".equals(message.senderType()))) return;
        assertEquals("AI_ACTIVE",response.state());
        assertEquals(1,providerRequests.get(),"Stop after the first provider failure; do not retry or send another live request.");
        assertTrue(response.messages().stream().anyMatch(message->message.senderType().equals("SYSTEM")
                && message.body().contains("não conseguiu responder agora")));
        System.out.printf("SUPPORT_GEMINI_LIVE outcome=provider-failure-category-see-sanitized-provider-log requests=%d state=%s%n",
                providerRequests.get(),response.state());
        throw new TestAbortedException("Provider unavailable; no additional live request was sent.");
    }

    private static GeminiRequestBudget sharedEvaluationBudget() throws Exception {
        Constructor<GeminiRequestBudget> constructor=GeminiRequestBudget.class.getDeclaredConstructor(
                int.class,int.class,long.class,int.class,int.class,Clock.class);
        constructor.setAccessible(true);
        return constructor.newInstance(4,4,200_000L,3_000,16_000,Clock.systemUTC());
    }

    private static String nonblank(String value,String fallback) {
        return value==null||value.isBlank()?fallback:value.strip();
    }

    /** No database or persistent private data is used by this capped real-provider evaluation. */
    static final class InMemoryRepository extends SupportConversationRepository {
        private Row current;
        private final List<SupportDtos.Message> messages=new ArrayList<>();
        private final List<SupportDtos.Transition> transitions=new ArrayList<>();
        private final Set<String> keys=new HashSet<>();
        private long nextId=1;
        InMemoryRepository(){super(mock(JdbcTemplate.class));}
        @Override public void lockUser(int userId){}
        @Override public int countOpenForUser(int userId){return current!=null&&!"CLOSED".equals(current.state())?1:0;}
        @Override public int countRecentMessagesForUser(int userId,Instant since){return 0;}
        @Override public int countMessages(UUID id){return messages.size();}
        @Override public synchronized Row create(UUID id,int owner){
            Instant now=Instant.now();current=new Row(id,owner,"AI_ACTIVE",null,0,now,now,now,now,null,null,false,false);
            event(id,"USER",owner,"CREATED",null,"AI_ACTIVE",null);return current;
        }
        @Override public synchronized java.util.Optional<Row> lock(UUID id){return current!=null&&current.id().equals(id)?java.util.Optional.of(current):java.util.Optional.empty();}
        @Override public synchronized java.util.Optional<Row> find(UUID id){return lock(id);}
        @Override public synchronized java.util.Optional<Row> owned(UUID id,int userId){return lock(id).filter(row->row.ownerUserId()!=null&&row.ownerUserId()==userId);}
        @Override public synchronized int transition(UUID id,long expected,String from,String to,Integer assignee,Instant at){
            if(current==null||!current.id().equals(id)||current.version()!=expected||!current.state().equals(from))return 0;
            current=new Row(id,current.ownerUserId(),to,assignee,expected+1,current.createdAt(),at,at,at,
                    "CLOSED".equals(to)?at:null,current.contentPurgedAt(),current.unreadUser(),current.unreadStaff());
            return 1;
        }
        @Override public synchronized int bumpForUserMessage(UUID id,long expected){
            if(current==null||current.version()!=expected||"CLOSED".equals(current.state()))return 0;
            Instant now=Instant.now();current=new Row(id,current.ownerUserId(),current.state(),current.assignedStaffUserId(),
                    expected+1,current.createdAt(),now,current.stateChangedAt(),now,current.closedAt(),current.contentPurgedAt(),false,current.unreadStaff());
            return 1;
        }
        @Override public synchronized boolean hasMessage(UUID id,String type,Integer senderId,String key){return keys.contains(type+":"+senderId+":"+key);}
        @Override public synchronized long addMessage(UUID id,String type,Integer senderId,String label,String body,String key){
            String unique=type+":"+senderId+":"+key;if(!keys.add(unique))return messages.getLast().id();
            Instant now=Instant.now();messages.add(new SupportDtos.Message(nextId++,type,label,body,now));
            if(current!=null)current=new Row(current.id(),current.ownerUserId(),current.state(),current.assignedStaffUserId(),
                    current.version(),current.createdAt(),now,current.stateChangedAt(),now,current.closedAt(),current.contentPurgedAt(),
                    current.unreadUser(),current.unreadStaff());
            return nextId-1;
        }
        @Override public synchronized List<SupportDtos.Message> messages(UUID id,int limit){
            int from=Math.max(0,messages.size()-limit);return List.copyOf(messages.subList(from,messages.size()));
        }
        @Override public synchronized void event(UUID id,String actor,Integer actorId,String type,String from,String to,String reason){
            transitions.add(new SupportDtos.Transition(type,from,to,actor,Instant.now()));
        }
        @Override public synchronized void markUserRead(UUID id,int userId){
            if(current!=null)current=new Row(current.id(),current.ownerUserId(),current.state(),current.assignedStaffUserId(),
                    current.version(),current.createdAt(),current.updatedAt(),current.stateChangedAt(),current.lastMessageAt(),
                    current.closedAt(),current.contentPurgedAt(),false,current.unreadStaff());
        }
        @Override public synchronized List<SupportDtos.Transition> transitions(UUID id){return List.copyOf(transitions);}
    }
}
