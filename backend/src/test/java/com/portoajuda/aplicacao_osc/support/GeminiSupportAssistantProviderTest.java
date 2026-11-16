package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.service.GeminiRequestBudget;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiSupportAssistantProviderTest {
    private static final String KEY = "synthetic-key-not-a-credential";
    private static final String MODEL = "gemini-3.1-flash-lite";
    private static final SupportDtos.SearchResult GUIDE = new SupportDtos.SearchResult(
            "guide:catalogo-ongs", "ORIENTAÇÃO", "Encontrar ONGs",
            "Ver todas as ONGs no catálogo público.", "/catalogo?categoria=ongs");

    @Test void sendsBoundedPublicContextWithSophieIdentityAndStrictToolFreeJson() throws Exception {
        var mapper = new ObjectMapper();
        RestClient.Builder builder = restClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiRequestBudget budget = budget();
        var provider = new GeminiSupportAssistantProvider(builder.build(), budget, mapper, KEY, MODEL, "Sophie", 12000);
        String structured = mapper.writeValueAsString(Map.of("answer", "Acesse o catálogo público.",
                "sourceIds", List.of(GUIDE.id()), "requestsHuman", false));
        String response = mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("content",
                Map.of("parts", List.of(Map.of("text", structured))))), "usageMetadata", Map.of("promptTokenCount", 120)));
        AtomicReference<String> capturedRequest = new AtomicReference<>();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("x-goog-api-key", KEY))
                .andExpect(request -> capturedRequest.set(((org.springframework.mock.http.client.MockClientHttpRequest)request).getBodyAsString()))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(response, MediaType.APPLICATION_JSON));

        SupportAssistantProvider.Reply reply = provider.respond(
                new SupportAssistantProvider.Input(UUID.randomUUID(), "Como encontro ONGs?", List.of(), List.of(GUIDE)));

        server.verify();
        assertEquals("Acesse o catálogo público.", reply.answer());
        assertEquals(List.of(GUIDE.id()), reply.sourceIds());
        assertFalse(reply.requestsHuman());
        assertTrue(capturedRequest.get().contains("Sophie"));
        assertTrue(capturedRequest.get().contains("UNTRUSTED_SOURCE"));
        assertTrue(capturedRequest.get().contains("responseSchema"));
        assertTrue(capturedRequest.get().contains("Responda primeiro à pergunta real"));
        assertTrue(capturedRequest.get().contains("endosso"));
        assertFalse(capturedRequest.get().contains("\"tools\""));
        verify(budget).reserve(anyLong());
        verify(budget).recordActual(any(), eq(120L));
    }

    @Test void interpretsTopicChangesIntoBoundedTypedCapabilities() throws Exception {
        var mapper=new ObjectMapper();
        RestClient.Builder builder=restClientBuilder();
        MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        var provider=new GeminiSupportAssistantProvider(builder.build(),budget(),mapper,KEY,MODEL,"Sophie",12000);
        String structured=mapper.writeValueAsString(Map.of("actions",List.of(
                Map.of("operation","SEARCH_PUBLIC","category","VOLUNTEERING","query","voluntariado remoto","cause","NONE","city","","topic","NONE","scope","CURRENT_QUERY"),
                Map.of("operation","OPEN_PAGE","category","NGO","query","","cause","ANIMAIS","city","","topic","NONE","scope","NONE"),
                Map.of("operation","NONE","category","NONE","query","","cause","NONE","city","","topic","NONE","scope","NONE"),
                Map.of("operation","SEARCH_PUBLIC","category","NGO","query","ignored extra","cause","ANIMAIS","city","","topic","NONE","scope","CURRENT_QUERY")),
                "clarificationNeeded",false,"clarificationQuestion","","explicitHumanRequest",false,"privacyBoundary",false,"boundaryKind","NONE"));
        String response=mapper.writeValueAsString(Map.of("candidates",List.of(Map.of("content",Map.of("parts",List.of(Map.of("text",structured)))))));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                "https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(response,MediaType.APPLICATION_JSON));
        var plan=provider.interpret(new SupportAssistantProvider.InterpretationInput("Na verdade quero voluntariado remoto.",List.of(
                new SupportDtos.Message(1,"USER","user","ONGs de leitura em Campinas",Instant.now()))));
        assertEquals(3,plan.capabilities().size());
        assertEquals("VOLUNTEERING",plan.capabilities().getFirst().category());
        assertEquals("voluntariado remoto",plan.capabilities().getFirst().query());
        assertFalse(plan.privacyBoundary());
        server.verify();
    }

    @Test void providerFailureIsSanitizedAndNeverAutomaticallyRetried() {
        RestClient.Builder builder = restClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiRequestBudget budget = budget();
        var provider = new GeminiSupportAssistantProvider(builder.build(), budget, new ObjectMapper(), KEY, MODEL, "Sophie", 12000);
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        var failure = assertThrows(SupportAssistantProvider.ProviderUnavailableException.class, () -> provider.respond(
                new SupportAssistantProvider.Input(UUID.randomUUID(), "Pergunta sintética", List.of(), List.of(GUIDE))));

        assertEquals("assistência temporariamente indisponível", failure.getMessage());
        assertEquals(SupportAssistantProvider.FailureKind.PROVIDER_SERVER,failure.kind());
        server.verify();
        verify(budget, times(1)).reserve(anyLong());
    }

    @Test void quotaFailureIsClassifiedWithoutLoggingOrRetryingProviderResponse() {
        RestClient.Builder builder=restClientBuilder();
        MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        var provider=new GeminiSupportAssistantProvider(builder.build(),budget(),new ObjectMapper(),KEY,MODEL,"Sophie",12000);
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"message\":\"synthetic private provider detail\"}}")
                        .contentType(MediaType.APPLICATION_JSON));
        var failure=assertThrows(SupportAssistantProvider.ProviderUnavailableException.class,()->provider.respond(
                new SupportAssistantProvider.Input(UUID.randomUUID(),"Synthetic rate limit question",List.of(),List.of(GUIDE))));
        assertEquals(SupportAssistantProvider.FailureKind.QUOTA_OR_RATE_LIMIT,failure.kind());
        assertFalse(failure.getMessage().contains("synthetic private provider detail"));
        server.verify();
    }

    @Test void noResultsPromptAsksForClarificationWithoutRequestingHumanHandoff() throws Exception {
        var mapper=new ObjectMapper();
        RestClient.Builder builder=restClientBuilder();
        MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        var provider=new GeminiSupportAssistantProvider(builder.build(),budget(),mapper,KEY,MODEL,"Sophie",12000);
        String structured=mapper.writeValueAsString(Map.of("answer","Você lembra a cidade ou a causa?","sourceIds",List.of(),"requestsHuman",false));
        String response=mapper.writeValueAsString(Map.of("candidates",List.of(Map.of("content",
                Map.of("parts",List.of(Map.of("text",structured)))))));
        AtomicReference<String> prompt=new AtomicReference<>();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent"))
                .andRespond(request->{
                    prompt.set(((org.springframework.mock.http.client.MockClientHttpRequest)request).getBodyAsString());
                    return org.springframework.test.web.client.response.MockRestResponseCreators
                            .withSuccess(response,MediaType.APPLICATION_JSON).createResponse(request);
                });

        var plan=new SupportAssistantProvider.Interpretation(List.of(new SupportAssistantProvider.CapabilityCall("SEARCH_PUBLIC","NGO","ONG de leitura","NONE","","NONE","CURRENT_QUERY")),false,"",false,false,"NONE");
        var reply=provider.respond(new SupportAssistantProvider.Input(UUID.randomUUID(),"N\u00e3o encontrei o que procuro.",List.of(),List.of(),false,plan));
        assertEquals("Você lembra a cidade ou a causa?",reply.answer());
        assertTrue(reply.sourceIds().isEmpty());
        assertFalse(reply.requestsHuman());
        assertTrue(prompt.get().contains("concluída sem correspondências"));
        server.verify();
    }

    @Test void unavailablePublicSearchIsNotPresentedAsAnEmptySearch() throws Exception {
        var mapper=new ObjectMapper();
        RestClient.Builder builder=restClientBuilder();
        MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        var provider=new GeminiSupportAssistantProvider(builder.build(),budget(),mapper,KEY,MODEL,"Sophie",12000);
        String structured=mapper.writeValueAsString(Map.of("answer","Não consegui consultar a busca pública agora.","sourceIds",List.of(),"requestsHuman",false));
        String response=mapper.writeValueAsString(Map.of("candidates",List.of(Map.of("content",
                Map.of("parts",List.of(Map.of("text",structured)))))));
        AtomicReference<String> prompt=new AtomicReference<>();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent"))
                .andRespond(request->{
                    prompt.set(((org.springframework.mock.http.client.MockClientHttpRequest)request).getBodyAsString());
                    return org.springframework.test.web.client.response.MockRestResponseCreators
                            .withSuccess(response,MediaType.APPLICATION_JSON).createResponse(request);
                });

        var reply=provider.respond(new SupportAssistantProvider.Input(UUID.randomUUID(),"Há ONGs perto de mim?",
                List.of(),List.of(),true));

        assertTrue(prompt.get().contains("indisponível temporariamente"));
        assertTrue(reply.answer().contains("Não consegui consultar"));
        assertFalse(reply.requestsHuman());
        server.verify();
    }

    @Test void redactsCredentialsAndPersonalContactDataBeforePromptConstruction() {
        String safe = GeminiSupportAssistantProvider.redact(
                "Senha: abc123, pessoa@example.test, CPF 123.456.789-09, telefone 11987654321, Meu nome é Ana Silva e CEP 11700-000.");
        assertFalse(safe.contains("abc123"));
        assertFalse(safe.contains("pessoa@example.test"));
        assertFalse(safe.contains("123.456.789-09"));
        assertFalse(safe.contains("11987654321"));
        assertFalse(safe.contains("Ana Silva"));
        assertFalse(safe.contains("11700-000"));
        assertTrue(safe.contains("[dado omitido]"));
    }

    private static GeminiRequestBudget budget() {
        GeminiRequestBudget budget = mock(GeminiRequestBudget.class);
        when(budget.reserve(anyLong())).thenReturn(new GeminiRequestBudget.Reservation(UUID.randomUUID(), Instant.now(), 100));
        doNothing().when(budget).recordActual(any(), anyLong());
        return budget;
    }

    private static RestClient.Builder restClientBuilder() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        builder.messageConverters(converters -> {
            converters.clear();
            converters.add(new JacksonJsonHttpMessageConverter(tools.jackson.databind.json.JsonMapper.builder().build()));
        });
        return builder;
    }
}
