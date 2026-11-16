package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.GeminiRequestBudget;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;

/** One capped real request using the local authorized public search endpoint and only public source DTOs. */
class SupportAssistantGeminiRecommendationsLiveTest {
    @Test void oneSyntheticMessageGeneratesGroundedAnimalNgoOptionsWithinOneRequest() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("SUPPORT_ASSISTANT_LIVE_GEMINI_CONFIRMED")),"Explicit live evaluation opt-in is required.");
        String key=System.getenv("GEMINI_API_KEY");Assumptions.assumeTrue(key!=null&&!key.isBlank(),"Configured Gemini credential is unavailable.");
        String model=System.getenv("SUPPORT_ASSISTANT_AI_MODEL");if(model==null||model.isBlank())model="gemini-3.1-flash-lite";
        ObjectMapper mapper=new ObjectMapper();AtomicInteger externalRequests=new AtomicInteger();Duration timeout=Duration.ofSeconds(45);
        RestClient.Builder builder=RestClient.builder();builder.messageConverters(converters->{converters.clear();converters.add(new JacksonJsonHttpMessageConverter(tools.jackson.databind.json.JsonMapper.builder().build()));});
        JdkClientHttpRequestFactory factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());factory.setReadTimeout(timeout);
        RestClient client=builder.requestFactory(factory).requestInterceptor((request,body,execution)->{if(externalRequests.incrementAndGet()>1)throw new IllegalStateException("Synthetic evaluation cap reached.");return execution.execute(request,body);}).build();
        GeminiRequestBudget budget=budget();GeminiSupportAssistantProvider gemini=new GeminiSupportAssistantProvider(client,budget,mapper,key,model,"Sophie",12000);
        var answerInput=new java.util.concurrent.atomic.AtomicReference<SupportAssistantProvider.Input>();
        SupportAssistantProvider provider=new SupportAssistantProvider(){
            @Override public Interpretation interpret(InterpretationInput input){return new Interpretation(List.of(new CapabilityCall("SEARCH_PUBLIC","NGO","ONGs que ajudam animais","NONE","","NONE","CURRENT_QUERY")),false,"",false,false,"NONE");}
            @Override public Reply respond(Input input){answerInput.set(input);return gemini.respond(input);}
        };
        var beanFactory=new StaticListableBeanFactory();beanFactory.addBean("supportProvider",provider);ObjectProvider<SupportAssistantProvider> providers=beanFactory.getBeanProvider(SupportAssistantProvider.class);
        SupportPublicContentSearch search=mock(SupportPublicContentSearch.class);
        when(search.search(any(),any(),any(),any())).thenAnswer(call->actualPublicSearch(call.getArgument(0),call.getArgument(1),mapper));
        when(search.search(any(),any())).thenAnswer(call->actualPublicSearch(call.getArgument(0),call.getArgument(1),mapper));
        PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);when(transactions.getTransaction(any(TransactionDefinition.class))).thenAnswer(call->new SimpleTransactionStatus());
        doNothing().when(transactions).commit(any(TransactionStatus.class));doNothing().when(transactions).rollback(any(TransactionStatus.class));
        var repository=new SupportAssistantGeminiLiveEvaluationTest.InMemoryRepository();
        var service=new SupportAssistantService(repository,search,mock(AdminAuthorizationService.class),mock(AdminAssignmentRepository.class),providers,budget,transactions,true,true,12,java.time.Clock.systemUTC(),"Sophie");
        Usuario syntheticUser=new Usuario();ReflectionTestUtils.setField(syntheticUser,"id",987_654_329);var conversation=service.create(syntheticUser);
        String question="Quais ONGs ajudam animais?";
        var result=service.send(conversation.id(),syntheticUser,new SupportDtos.UserMessageRequest(question,UUID.randomUUID().toString()));
        String answer=lastAi(result.messages());SupportAssistantProvider.Input input=answerInput.get();
        assertEquals("AI_ACTIVE",result.state());assertEquals(1,externalRequests.get());assertNotNull(input);
        assertTrue(input.outcomes().stream().anyMatch(outcome->"NGO".equals(outcome.category())&&"RESULTS".equals(outcome.status())));
        List<SupportDtos.SearchResult> options=input.publicSources().stream().filter(source->"ONG".equals(source.category())).limit(3).toList();
        assertFalse(options.isEmpty());
        for(SupportDtos.SearchResult option:options){assertTrue(answer.contains(option.title()));assertTrue(answer.contains(option.summary()));assertTrue(answer.contains(option.href()));}
        assertFalse(SupportPublicContentSearch.normalize(answer).contains("vai ao catalogo para procurar"));
        assertFalse(SupportPublicContentSearch.normalize(answer).contains("mais confiavel"));
        assertFalse(answer.lines().anyMatch(line->line.strip().equals("/")));
        System.out.printf("SOPHIE_LIVE_GENERATION requests=%d publicAnimalOptions=%d answerChars=%d synthetic=true planner=mocked%n",externalRequests.get(),options.size(),answer.length());
        System.out.println("SYNTHETIC_EXCHANGE user="+question+"\nSophie="+answer);
    }
    private static List<SupportDtos.SearchResult> actualPublicSearch(String query,String category,ObjectMapper mapper) throws Exception {
        String encoded=URLEncoder.encode(query,StandardCharsets.UTF_8);
        HttpRequest request=HttpRequest.newBuilder(URI.create("http://localhost:8080/support-assistant/public-search?query="+encoded+"&category="+category)).GET().build();
        HttpResponse<String> response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode(),"Authorized public search must succeed before provider output is accepted.");
        var type=mapper.getTypeFactory().constructCollectionType(List.class,SupportDtos.SearchResult.class);
        return mapper.readValue(response.body(),type);
    }

    private static String lastAi(List<SupportDtos.Message> messages) {
        return messages.stream().filter(message->"AI".equals(message.senderType())).reduce((first,last)->last).orElseThrow().body();
    }
    private static GeminiRequestBudget budget() throws Exception {
        Constructor<GeminiRequestBudget> constructor = GeminiRequestBudget.class.getDeclaredConstructor(
                int.class, int.class, long.class, int.class, int.class, java.time.Clock.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1, 1, 200_000L, 3_000, 16_000, java.time.Clock.systemUTC());
    }
}
