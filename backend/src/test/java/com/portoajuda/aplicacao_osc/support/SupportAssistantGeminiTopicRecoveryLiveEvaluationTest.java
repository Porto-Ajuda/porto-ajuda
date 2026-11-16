package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;

import tools.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.service.GeminiRequestBudget;
import java.lang.reflect.Constructor;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

/** One additional synthetic-only provider call after a bounded timeout; no retries or stored conversations. */
class SupportAssistantGeminiTopicRecoveryLiveEvaluationTest {
    @Test void evaluatesOneContextualTopicChangeWithA45SecondBoundAndOneCallCap() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("SUPPORT_ASSISTANT_LIVE_GEMINI_CONFIRMED")),
                "Explicit live-evaluation opt-in is required.");
        String key = System.getenv("GEMINI_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "Configured Gemini credential is unavailable.");
        String model = System.getenv("SUPPORT_ASSISTANT_AI_MODEL");
        if (model == null || model.isBlank()) model = "gemini-3.1-flash-lite";
        ObjectMapper mapper = new ObjectMapper();
        GeminiRequestBudget budget = budget();
        AtomicInteger requests = new AtomicInteger();
        RestClient.Builder builder = RestClient.builder();
        builder.messageConverters(converters -> {
            converters.clear();
            converters.add(new JacksonJsonHttpMessageConverter(tools.jackson.databind.json.JsonMapper.builder().build()));
        });
        Duration timeout = Duration.ofSeconds(45);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        RestClient client = builder.requestFactory(factory).requestInterceptor((request, body, execution) -> {
            if (requests.incrementAndGet() > 1) throw new IllegalStateException("Synthetic request cap reached.");
            return execution.execute(request, body);
        }).build();
        GeminiSupportAssistantProvider provider = new GeminiSupportAssistantProvider(client, budget, mapper, key,
                model, "Sophie", 12000);
        var history = List.of(
                new SupportDtos.Message(1, "USER", "Você", "Como encontro ONGs de leitura em Praia Grande?", Instant.now()),
                new SupportDtos.Message(2, "AI", "Sophie · resposta automática", "Você pode explorar o catálogo de ONGs e abrir os perfis públicos.", Instant.now()),
                new SupportDtos.Message(3, "USER", "Você", "Agora quero mudar de assunto para voluntariado remoto.", Instant.now()),
                new SupportDtos.Message(4, "AI", "Sophie · resposta automática", "Claro, podemos mudar de assunto.", Instant.now()));
        SupportAssistantProvider.Reply reply;
        try {
            reply = provider.respond(new SupportAssistantProvider.Input(UUID.randomUUID(),
                    "Como posso encontrar oportunidades remotas?", history, List.of(), false));
        } catch (SupportAssistantProvider.ProviderUnavailableException unavailable) {
            System.out.printf("SUPPORT_GEMINI_TOPIC_RECOVERY outcome=unavailable category=%s requests=%d timeoutSeconds=45%n",
                    unavailable.kind(), requests.get());
            throw unavailable;
        }
        assertEquals(1, requests.get(), "No automatic retry is allowed in this live evaluation.");
        assertNotNull(reply);
        assertFalse(reply.answer().isBlank());
        assertTrue(reply.answer().length() <= 2400);
        assertTrue(reply.sourceIds().isEmpty(), "The provider has no current public results and cannot invent links.");
        System.out.printf("SUPPORT_GEMINI_TOPIC_RECOVERY outcome=success requests=%d answerCharacters=%d sourceIds=%d model=%s timeoutSeconds=45%n",
                requests.get(), reply.answer().length(), reply.sourceIds().size(), model);
    }

    private static GeminiRequestBudget budget() throws Exception {
        Constructor<GeminiRequestBudget> constructor = GeminiRequestBudget.class.getDeclaredConstructor(
                int.class, int.class, long.class, int.class, int.class, java.time.Clock.class);
        constructor.setAccessible(true);
        return constructor.newInstance(4, 4, 200_000L, 3_000, 16_000, java.time.Clock.systemUTC());
    }
}
