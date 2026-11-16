package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.admin.AdminProfile;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.GeminiRequestBudget;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class SupportAssistantServiceTest {
    private static final SupportDtos.SearchResult SOURCE = new SupportDtos.SearchResult(
            "ngo:3", "ONG", "ONG teste", "Conteúdo público de teste", "/ong-profile?id=3");
    private static final SupportDtos.SearchResult PROFILE_GUIDE = new SupportDtos.SearchResult(
            "guide:profile", "ORIENTAÇÃO", "Abrir perfil", "Entre na conta e abra Perfil.", "/perfil");
    private static final SupportDtos.SearchResult ANIMAL_NGO_ONE = new SupportDtos.SearchResult(
            "ngo:782101", "ONG", "Abrigo Fictício Patas", "Causas: proteção animal. Local: Praia Grande. SP. Acolhe cães e gatos resgatados.", "/ong-profile?id=782101");
    private static final SupportDtos.SearchResult ANIMAL_NGO_TWO = new SupportDtos.SearchResult(
            "ngo:782102", "ONG", "Projeto Fictício Fauna", "Causas: proteção animal. Local: Santos. SP. Reabilita fauna silvestre.", "/ong-profile?id=782102");

    @Test void requestHumanMovesConversationToQueueAndRecordsMessage() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        Usuario user = user(20);
        SupportAssistantService service = service(repo, false, null, List.of());
        var result = service.requestHuman(repo.current.id(), user, new SupportDtos.ActionRequest("Preciso falar com a equipe."));
        assertEquals("WAITING_HUMAN", result.state());
        assertTrue(result.messages().stream().anyMatch(m -> m.senderType().equals("SYSTEM")));
        assertTrue(repo.events.contains("AI_ACTIVE>WAITING_HUMAN"));
    }

    @Test void newConversationStoresOneSophieGreetingForReloads() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantService service = service(repo, false, null, List.of());
        var created = service.create(user(20));
        assertEquals(1, created.messages().stream().filter(message -> message.body().equals(
                "Olá! Sou a Sophie, assistente virtual do Porto Ajuda. Como posso ajudar?")).count());
        assertEquals("SYSTEM", created.messages().getFirst().senderType());
        assertEquals("Sophie · mensagem automática", created.messages().getFirst().senderLabel());
        assertEquals(1, service.mine(created.id(), user(20)).messages().stream()
                .filter(message -> message.body().equals("Olá! Sou a Sophie, assistente virtual do Porto Ajuda. Como posso ajudar?")).count());
    }

    @Test void introductionUsesConfiguredAssistantDisplayNameAndAiAvailability() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        var service = service(repo, false, null, List.of());
        var intro = service.intro();
        assertEquals("Sophie", intro.assistantName());
        assertTrue(intro.conversationsEnabled());
        assertFalse(intro.aiAvailable());
    }

    @Test void greetingUsesNaturalProviderReplyWithoutUnnecessaryPublicSearchOrReintroduction() {
        MemoryRepository repo=new MemoryRepository("AI_ACTIVE",20);
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply(
                "Oi! Tudo certo por aqui — em que posso ajudar?",List.of(),false));
        SupportAssistantService service=service(repo,true,provider,List.of(SOURCE));
        SupportPublicContentSearch search=(SupportPublicContentSearch)ReflectionTestUtils.getField(service,"search");
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest(
                "Oi, tudo bem?",UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE",result.state());
        assertTrue(result.messages().stream().anyMatch(message -> message.senderType().equals("AI")
                && message.body().equals("Oi! Tudo certo por aqui — em que posso ajudar?")));
        verifyNoInteractions(search);
        verify(provider).respond(argThat(input -> input.publicSources().isEmpty()
                && input.history().stream().noneMatch(message -> message.senderType().equals("USER")
                        && message.body().equals("Oi, tudo bem?"))));
    }

    @Test void waitingConversationAcceptsMoreMessagesWithoutCallingAi() {
        MemoryRepository repo = new MemoryRepository("WAITING_HUMAN", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        SupportAssistantService service = service(repo, true, provider, List.of(SOURCE));
        var result = service.send(repo.current.id(), user(20), new SupportDtos.UserMessageRequest("Mais contexto público", UUID.randomUUID().toString()));
        assertEquals("WAITING_HUMAN", result.state());
        assertEquals(1, repo.messages.stream().filter(m -> m.senderType().equals("USER")).count());
        assertTrue(result.messages().stream().anyMatch(message -> message.senderType().equals("USER") && message.body().equals("Mais contexto público")));
        verifyNoInteractions(provider);
    }

    @Test void humanActiveConversationAcceptsMessageWithoutCallingAiOrMixingReplies() {
        MemoryRepository repo=new MemoryRepository("HUMAN_ACTIVE",20);
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        SupportAssistantService service=service(repo,true,provider,List.of(SOURCE));
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest(
                "Tenho mais um detalhe para a pessoa atendente.",UUID.randomUUID().toString()));
        assertEquals("HUMAN_ACTIVE",result.state());
        assertTrue(result.messages().stream().anyMatch(message->message.senderType().equals("USER")
                && message.body().equals("Tenho mais um detalhe para a pessoa atendente.")));
        assertFalse(result.messages().stream().anyMatch(message->message.senderType().equals("AI")));
        verifyNoInteractions(provider);
    }

    @Test void takeoverDuringProviderCallSuppressesStaleAiResponse() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        Usuario user = user(20);
        SupportAssistantService[] holder = new SupportAssistantService[1];
        SupportAssistantProvider provider = input -> {
            holder[0].requestHuman(repo.current.id(), user, new SupportDtos.ActionRequest("Necessito de atendimento humano."));
            return new SupportAssistantProvider.Reply("Resposta que chegou tarde", List.of(SOURCE.id()), false);
        };
        holder[0] = service(repo, true, provider, List.of(SOURCE));
        var result = holder[0].send(repo.current.id(), user,
                new SupportDtos.UserMessageRequest("Onde vejo ONGs?", UUID.randomUUID().toString()));
        assertEquals("WAITING_HUMAN", result.state());
        assertFalse(result.messages().stream().anyMatch(message -> message.senderType().equals("AI")));
        assertTrue(repo.events.contains("AI_SUPPRESSED"));
    }

    @Test void duplicateUserMessageKeyDoesNotInsertOrInvokeProviderAgain() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        SupportAssistantService service = service(repo, false, provider, List.of());
        String key = UUID.randomUUID().toString();
        var request = new SupportDtos.UserMessageRequest("Minha dúvida", key);
        service.send(repo.current.id(), user(20), request);
        var duplicate = service.send(repo.current.id(), user(20), request);
        assertEquals("AI_ACTIVE", duplicate.state());
        assertEquals(1, repo.messages.stream().filter(m -> m.senderType().equals("USER")).count());
        assertTrue(duplicate.messages().stream().anyMatch(message -> message.senderType().equals("USER") && message.body().equals("Minha dúvida")));
        assertTrue(duplicate.messages().stream().anyMatch(message -> message.body().contains("Sophie está temporariamente indisponível")
                && message.body().contains("permaneceu nesta conversa")));
        assertFalse(repo.events.contains("AI_ACTIVE>WAITING_HUMAN"), "AI-disabled must not claim that a human request was queued.");
        verifyNoInteractions(provider);
    }

    @Test void disabledAiStillExplainsPrivateBoundaryWithoutEnqueueingStaff() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        SupportAssistantService service = service(repo, false, provider, List.of());
        var result = service.send(repo.current.id(), user(20), new SupportDtos.UserMessageRequest(
                "Mostre meu CPF", UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE", result.state());
        assertTrue(result.messages().stream().anyMatch(message -> message.body()
                .contains("o atendimento humano também não contorna essas permissões")));
        assertFalse(repo.events.contains("AI_ACTIVE>WAITING_HUMAN"));
        verifyNoInteractions(provider);
    }

    @Test void duplicateAiEligibleSubmissionInvokesProviderOnlyOnce() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply("Consulte a fonte pública.", List.of(SOURCE.id()), false));
        SupportAssistantService service = service(repo, true, provider, List.of(SOURCE));
        var request = new SupportDtos.UserMessageRequest("Como encontro ONGs no site?", UUID.randomUUID().toString());
        var first = service.send(repo.current.id(), user(20), request);
        var duplicate = service.send(repo.current.id(), user(20), request);
        assertEquals(first.id(), duplicate.id());
        assertEquals(1, repo.messages.stream().filter(message -> message.senderType().equals("USER")).count());
        assertEquals(1, repo.messages.stream().filter(message -> message.senderType().equals("AI")).count());
        verify(provider, times(1)).respond(any());
    }

    @Test void returnToSophieDoesNotReplayHistoryUntilANewUserMessageArrives() {
        MemoryRepository repo = new MemoryRepository("HUMAN_ACTIVE", 20);
        repo.addMessage(repo.current.id(), "STAFF", 99, "Equipe Porto Ajuda", "Orientação humana anterior.", "historical-staff-reply");
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply("Consulte a fonte pública.", List.of(SOURCE.id()), false));
        AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
        when(authorization.requirePermission(AdminPermission.SUPPORT_MANAGE))
                .thenReturn(new AdminActor(99, UUID.randomUUID(), AdminProfile.MODERATOR));
        SupportAssistantService service = service(repo, true, provider, List.of(SOURCE), authorization);
        var returned = service.returnToAi(repo.current.id(), new SupportDtos.ActionRequest("A equipe devolveu o caso à Sophie."));
        assertEquals("AI_ACTIVE", returned.state());
        assertTrue(returned.messages().stream().anyMatch(message -> message.body().equals("Orientação humana anterior.")));
        verifyNoInteractions(provider);
        var afterNewQuestion = service.send(repo.current.id(), user(20), new SupportDtos.UserMessageRequest(
                "Nova pergunta sobre ONGs", UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE", afterNewQuestion.state());
        verify(provider, times(1)).respond(any());
    }

    @Test void financialHowToUsesGroundedGuidanceWithoutAttemptingPaymentOrHandoff() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.interpret(any())).thenReturn(new SupportAssistantProvider.Interpretation(List.of(
                new SupportAssistantProvider.CapabilityCall("SITE_GUIDANCE","NONE","","NONE","","DONATIONS","NONE")),false,"",false,false,"NONE"));
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply(
                "Não consigo iniciar pagamentos pelo chat; posso orientar apenas pelo que a plataforma oferece.",List.of("help:donations"),false));
        SupportAssistantService service = service(repo, true, provider, List.of());
        SupportPublicContentSearch search=(SupportPublicContentSearch)ReflectionTestUtils.getField(service,"search");
        when(search.help("DONATIONS")).thenReturn(List.of(new SupportDtos.SearchResult("help:donations","ORIENTACAO","Doações",
                "Sophie não inicia pagamentos nem confirma campanhas ou destinos financeiros.","/catalogo?categoria=doacoes")));
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest(
                "Como faço um pagamento por Pix?",UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE",result.state());
        assertFalse(repo.events.contains("AI_ACTIVE>WAITING_HUMAN"));
        assertTrue(result.messages().stream().anyMatch(message->message.senderType().equals("AI")
                &&message.body().contains("Não consigo iniciar pagamentos")));
        verify(search,never()).count(any(),any(),nullable(com.portoajuda.aplicacao_osc.enums.CausaSocial.class),anyString());
    }
    @Test void navigationToOwnProfileUsesOnlyTheAuthenticatedPublicGuide() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply(
                "Entre na sua conta e abra Perfil.", List.of(PROFILE_GUIDE.id()), false));
        SupportAssistantService service = service(repo, true, provider, List.of(PROFILE_GUIDE));
        var result = service.send(repo.current.id(), user(20), new SupportDtos.UserMessageRequest(
                "Como acesso meu perfil?", UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE", result.state());
        assertTrue(result.messages().stream().anyMatch(message -> message.senderType().equals("AI")
                && message.body().contains("/perfil")));
        verify(provider).respond(argThat(input -> input.publicSources().stream()
                .anyMatch(source -> source.id().equals("guide:profile"))));
    }

    @Test void providerFailureKeepsConversationActiveAndExplicitRetryCallsProviderOnce() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenThrow(new SupportAssistantProvider.ProviderUnavailableException(
                SupportAssistantProvider.FailureKind.TIMEOUT)).thenReturn(
                        new SupportAssistantProvider.Reply("Posso ajudar com isso.", List.of(), false));
        SupportAssistantService service = service(repo, true, provider, List.of());
        Usuario user = user(20);
        var first = service.send(repo.current.id(), user,
                new SupportDtos.UserMessageRequest("Como uso o site?", UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE", first.state());
        assertTrue(first.messages().stream().anyMatch(message -> message.body().contains("não conseguiu responder agora")));
        assertFalse(repo.events.contains("AI_ACTIVE>WAITING_HUMAN"));

        var retried = service.retryAi(repo.current.id(), user);
        var duplicateRetry = service.retryAi(repo.current.id(), user);
        assertEquals("AI_ACTIVE", retried.state());
        assertTrue(retried.messages().stream().anyMatch(message -> message.senderType().equals("AI")
                && message.body().equals("Posso ajudar com isso.")));
        assertEquals(1, retried.messages().stream().filter(message -> message.senderType().equals("USER")).count());
        assertEquals(retried.messages().stream().filter(message -> message.senderType().equals("AI")).count(),
                duplicateRetry.messages().stream().filter(message -> message.senderType().equals("AI")).count());
        verify(provider, times(2)).respond(any());
    }

    @Test void explicitHumanRequestHandsOffBeforeCallingProvider() {
        MemoryRepository repo=new MemoryRepository("AI_ACTIVE",20);
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        SupportAssistantService service=service(repo,true,provider,List.of(SOURCE));
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest(
                "Quero falar com uma pessoa atendente.",UUID.randomUUID().toString()));
        assertEquals("WAITING_HUMAN",result.state());
        assertTrue(result.messages().stream().anyMatch(message->message.senderType().equals("SYSTEM")
                && message.body().contains("encaminhada para a equipe")));
        verifyNoInteractions(provider);
    }

    @Test void groundedAiReplyPersistsOnlyServerValidatedInternalReferences() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply("Consulte a página pública.", List.of(SOURCE.id()), false));
        SupportAssistantService service = service(repo, true, provider, List.of(SOURCE));
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest("ONG Esperança",UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE",result.state());
        var ai=result.messages().stream().filter(message->message.senderType().equals("AI")).findFirst().orElseThrow();
        assertTrue(ai.body().contains("Consulte a página pública."));
        assertTrue(ai.body().contains("/ong-profile?id=3"));
        verify(provider,times(1)).respond(any());
    }

    @Test void resultNamesAlreadyUsedByTheModelBecomeLinksWithoutRepeatingFactsOrNextSteps() {
        MemoryRepository repo=new MemoryRepository("AI_ACTIVE",20);
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply(
                "O Abrigo Fictício Patas registra proteção animal em Praia Grande; o Projeto Fictício Fauna também atua com animais em Santos. Quer que eu abra algum perfil?",List.of(),false));
        SupportAssistantService service=service(repo,true,provider,List.of(ANIMAL_NGO_ONE,ANIMAL_NGO_TWO));
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest("Quais ONGs ajudam animais?",UUID.randomUUID().toString()));
        String answer=result.messages().stream().filter(message->"AI".equals(message.senderType())).reduce((a,b)->b).orElseThrow().body();
        assertEquals(1,answer.split("Abrigo Fictício Patas",-1).length-1);
        assertEquals(1,answer.split("Projeto Fictício Fauna",-1).length-1);
        assertTrue(answer.contains("[Abrigo Fictício Patas](/ong-profile?id=782101)"));
        assertTrue(answer.contains("[Projeto Fictício Fauna](/ong-profile?id=782102)"));
        assertFalse(answer.contains("Causas registradas no perfil público"));
        assertFalse(answer.contains("Posso comparar essas opções"));
    }
    @Test void animalRecommendationsListRealMatchedPublicSourcesAndValidatedDirectLinks() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply(
                "Encontrei algumas opções no catálogo. Confira os filtros.", List.of(), false));
        SupportAssistantService service = service(repo, true, provider, List.of(ANIMAL_NGO_ONE, ANIMAL_NGO_TWO));

        var result = service.send(repo.current.id(), user(20), new SupportDtos.UserMessageRequest(
                "Quais ONGs ajudam animais?", UUID.randomUUID().toString()));
        String answer = result.messages().stream().filter(message -> message.senderType().equals("AI"))
                .reduce((first, last) -> last).orElseThrow().body();

        assertTrue(answer.contains("Abrigo Fictício Patas"));
        assertTrue(answer.contains("Projeto Fictício Fauna"));
        assertTrue(answer.contains("proteção animal"));
        assertTrue(answer.contains("Praia Grande"));
        assertTrue(answer.contains("comparar essas opções"));
        assertFalse(answer.contains("filtre"), "A catalog instruction cannot replace the matching options.");
        assertFalse(SupportPublicContentSearch.normalize(answer).contains("filtre voce mesma"));
        assertTrue(answer.contains("/ong-profile?id=782101"));
        assertTrue(answer.contains("/ong-profile?id=782102"));
        assertTrue(answer.contains("causa pré-selecionada: proteção animal"));
        assertTrue(answer.contains("/catalogo?categoria=ongs&filtro=ANIMAIS"));
        assertFalse(answer.lines().anyMatch(line -> line.strip().equals("/")));
    }

    @Test void modelCannotTurnRootSlashIntoARecommendationLink() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply("/", List.of("guide:explore"), false));
        SupportAssistantService service = service(repo, true, provider, List.of(
                new SupportDtos.SearchResult("guide:explore", "ORIENTAÇÃO", "Explorar", "Use as opções públicas.", "/")));

        var result = service.send(repo.current.id(), user(20), new SupportDtos.UserMessageRequest(
                "Como uso o site?", UUID.randomUUID().toString()));
        String answer = result.messages().stream().filter(message -> message.senderType().equals("AI"))
                .findFirst().orElseThrow().body();
        assertFalse(answer.contains("Saiba mais:\n/"));
        assertFalse(answer.strip().equals("/"));
    }

    @Test void unsupportedTrustOrDonationSafetyClaimsAreReplacedBeforeDelivery() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply(
                "Esta é a ONG mais confiável e segura para doar.", List.of(ANIMAL_NGO_ONE.id()), false));
        SupportAssistantService service = service(repo, true, provider, List.of(ANIMAL_NGO_ONE));

        var result = service.send(repo.current.id(), user(20), new SupportDtos.UserMessageRequest(
                "Quais ONGs ajudam animais?", UUID.randomUUID().toString()));
        String answer = result.messages().stream().filter(message -> message.senderType().equals("AI"))
                .findFirst().orElseThrow().body();

        assertFalse(SupportPublicContentSearch.normalize(answer).contains("mais confiavel"));
        assertTrue(answer.contains("presença na plataforma não confirma confiabilidade"));
        assertTrue(answer.contains("Abrigo Fictício Patas"));
    }

    @Test void modelCanOfferHumanHelpButCannotQueueWithoutUserConfirmation() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply(
                "Este caso pode precisar de análise da equipe.", List.of(SOURCE.id()), true));
        SupportAssistantService service = service(repo, true, provider, List.of(SOURCE));
        var result = service.send(repo.current.id(), user(20), new SupportDtos.UserMessageRequest(
                "Tenho uma situação delicada sobre uma ONG", UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE", result.state());
        assertTrue(result.messages().stream().anyMatch(message -> message.senderType().equals("AI")
                && message.body().contains("Falar com uma pessoa")));
        assertFalse(repo.events.contains("AI_ACTIVE>WAITING_HUMAN"));
    }

    @Test void missingPublicResultsProduceAConversationalNextStepWithoutHumanEscalation() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply(
                "Não encontrei resultados públicos para essa busca. Você lembra a cidade ou a causa? Posso tentar de novo.",
                List.of(), false));
        SupportAssistantService service=service(repo,true,provider,List.of());
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest("Assunto sem referência pública",UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE",result.state());
        assertTrue(result.messages().stream().anyMatch(message->message.senderType().equals("AI")
                && message.body().contains("Você lembra a cidade")));
        assertFalse(result.messages().stream().anyMatch(message->message.senderType().equals("SYSTEM")
                && message.body().contains("encaminhada para a equipe")));
        verify(provider).respond(argThat(input -> input.publicSources().isEmpty()
                && !input.publicSearchUnavailable()
                && input.history().stream().noneMatch(message -> message.senderType().equals("USER")
                        && message.body().equals("Assunto sem referência pública"))));
    }

    @Test void publicSearchFailureIsPassedToTheModelAndNotMisreportedAsEmptyResults() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenAnswer(invocation -> {
            SupportAssistantProvider.Input input = invocation.getArgument(0);
            assertTrue(input.publicSearchUnavailable());
            return new SupportAssistantProvider.Reply("Não consegui consultar a busca pública agora.", List.of(), false);
        });
        SupportAssistantService service=service(repo,true,provider,List.of());
        SupportPublicContentSearch search=(SupportPublicContentSearch)ReflectionTestUtils.getField(service,"search");
        when(search.search(any(),any())).thenThrow(new IllegalStateException("synthetic search outage"));
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest(
                "Qual ONG trabalha com leitura?",UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE",result.state());
        assertTrue(result.messages().stream().anyMatch(message->message.senderType().equals("AI")
                && message.body().equals("Não consegui consultar a busca pública agora.")));
        verify(provider).respond(any());
    }

    @Test void followUpUsesBoundedConversationContextWithoutReplayingLatestQuestion() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        repo.addMessage(repo.current.id(),"USER",20,"Você","Encontre ONGs de leitura.","prior-question");
        repo.addMessage(repo.current.id(),"AI",null,"Sophie · resposta automática","Veja o catálogo público.","prior-answer");
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        when(provider.interpret(any())).thenReturn(new SupportAssistantProvider.Interpretation(List.of(
                new SupportAssistantProvider.CapabilityCall("SEARCH_PUBLIC","NGO","ONGs de leitura","NONE","Praia Grande","NONE","CURRENT_QUERY")),false,"",false,false,"NONE"));
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply(
                "Encontrei uma opção próxima do local informado.",List.of(SOURCE.id()),false));
        SupportAssistantService service=service(repo,true,provider,List.of(SOURCE));
        SupportPublicContentSearch search=(SupportPublicContentSearch)ReflectionTestUtils.getField(service,"search");
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest(
                "E perto de Praia Grande?",UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE",result.state());
        verify(search).search(eq("ONGs de leitura"),eq("NGO"),nullable(com.portoajuda.aplicacao_osc.enums.CausaSocial.class),eq("Praia Grande"));
        verify(provider).interpret(argThat(input -> input.latestQuestion().contains("Praia Grande")
                && input.history().stream().anyMatch(message -> message.body().equals("Encontre ONGs de leitura."))));
        verify(provider).respond(argThat(input -> input.history().stream().anyMatch(message ->
                        message.body().equals("Encontre ONGs de leitura."))
                && input.history().stream().noneMatch(message -> message.senderType().equals("USER")
                        && message.body().equals("E perto de Praia Grande?"))
                && input.latestQuestion().contains("Praia Grande")));
    }

    @Test void topicChangeSearchesTheNewTopicButKeepsConversationHistoryForTheModel() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        repo.addMessage(repo.current.id(),"USER",20,"Você","Encontre ONGs de leitura.","prior-question");
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        when(provider.interpret(any())).thenReturn(new SupportAssistantProvider.Interpretation(List.of(
                new SupportAssistantProvider.CapabilityCall("SEARCH_PUBLIC","VOLUNTEERING","voluntariado remoto","NONE","","NONE","CURRENT_QUERY")),false,"",false,false,"NONE"));
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply(
                "Posso procurar oportunidades de voluntariado remoto.",List.of(SOURCE.id()),false));
        SupportAssistantService service=service(repo,true,provider,List.of(SOURCE));
        SupportPublicContentSearch search=(SupportPublicContentSearch)ReflectionTestUtils.getField(service,"search");
        service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest(
                "Agora quero saber sobre voluntariado remoto.",UUID.randomUUID().toString()));
        verify(search).search("voluntariado remoto", "VOLUNTEERING");
        verify(provider).respond(argThat(input -> input.history().stream()
                .anyMatch(message -> message.body().equals("Encontre ONGs de leitura."))));
    }

    @Test void currentQuestionCauseRecoversSearchWhenPlannerOmitsKnownCauseWithoutReusingCorrectedTopic() {
        MemoryRepository repo=new MemoryRepository("AI_ACTIVE",20);
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        when(provider.interpret(any())).thenReturn(new SupportAssistantProvider.Interpretation(List.of(
                new SupportAssistantProvider.CapabilityCall("SEARCH_PUBLIC","NGO","ONGs","NONE","","NONE","CURRENT_QUERY")),false,"",false,false,"NONE"));
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply("Encontrei opções alinhadas.",List.of(),false));
        SupportAssistantService service=service(repo,true,provider,List.of(ANIMAL_NGO_ONE));
        SupportPublicContentSearch search=(SupportPublicContentSearch)ReflectionTestUtils.getField(service,"search");
        service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest("Quais ONGs ajudam animais?",UUID.randomUUID().toString()));
        verify(search).search(eq("ONGs animais"),eq("NGO"),eq(com.portoajuda.aplicacao_osc.enums.CausaSocial.ANIMAIS),eq(""));

        MemoryRepository correctedRepo=new MemoryRepository("AI_ACTIVE",20);
        SupportAssistantProvider correctedProvider=mock(SupportAssistantProvider.class);
        when(correctedProvider.interpret(any())).thenReturn(new SupportAssistantProvider.Interpretation(List.of(
                new SupportAssistantProvider.CapabilityCall("SEARCH_PUBLIC","VOLUNTEERING","voluntariado remoto","NONE","","NONE","CURRENT_QUERY")),false,"",false,false,"NONE"));
        when(correctedProvider.respond(any())).thenReturn(new SupportAssistantProvider.Reply("Vou verificar as vagas remotas.",List.of(),false));
        SupportAssistantService correctedService=service(correctedRepo,true,correctedProvider,List.of());
        SupportPublicContentSearch correctedSearch=(SupportPublicContentSearch)ReflectionTestUtils.getField(correctedService,"search");
        correctedService.send(correctedRepo.current.id(),user(20),new SupportDtos.UserMessageRequest("Esquece os animais; agora quero vagas remotas.",UUID.randomUUID().toString()));
        verify(correctedSearch).search("voluntariado remoto","VOLUNTEERING");
    }
    @Test void countDistinguishesPreviouslyShownOptionsFromSiteWideQuery() {
        MemoryRepository previousRepo=new MemoryRepository("AI_ACTIVE",20);
        previousRepo.addMessage(previousRepo.current.id(),"AI",null,"Sophie","Opções relacionadas:\n• Ver perfil de [ONG A](/ong-profile?id=782301) — causa animal.\n• Ver perfil de [ONG B](/ong-profile?id=782302) — causa animal.","prior-results");
        SupportAssistantProvider previousProvider=mock(SupportAssistantProvider.class);
        when(previousProvider.interpret(any())).thenReturn(new SupportAssistantProvider.Interpretation(List.of(
                new SupportAssistantProvider.CapabilityCall("COUNT_PUBLIC","NGO","","ANIMAIS","","NONE","PREVIOUS_RESULTS")),false,"",false,false,"NONE"));
        when(previousProvider.respond(any())).thenReturn(new SupportAssistantProvider.Reply("Você viu duas opções.",List.of(),false));
        SupportAssistantService previousService=service(previousRepo,true,previousProvider,List.of());
        var previousResult=previousService.send(previousRepo.current.id(),user(20),new SupportDtos.UserMessageRequest("Quantas delas?",UUID.randomUUID().toString()));
        String previousAnswer=previousResult.messages().stream().filter(message->"AI".equals(message.senderType())).reduce((a,b)->b).orElseThrow().body();
        assertTrue(previousAnswer.contains("foram apresentadas 2 opções públicas"));
        SupportPublicContentSearch previousSearch=(SupportPublicContentSearch)ReflectionTestUtils.getField(previousService,"search");
        verify(previousSearch,never()).count(any(),any(),nullable(com.portoajuda.aplicacao_osc.enums.CausaSocial.class),anyString());

        MemoryRepository siteRepo=new MemoryRepository("AI_ACTIVE",20);
        SupportAssistantProvider siteProvider=mock(SupportAssistantProvider.class);
        when(siteProvider.interpret(any())).thenReturn(new SupportAssistantProvider.Interpretation(List.of(
                new SupportAssistantProvider.CapabilityCall("COUNT_PUBLIC","NGO","","NONE","","NONE","CURRENT_QUERY")),false,"",false,false,"NONE"));
        when(siteProvider.respond(any())).thenReturn(new SupportAssistantProvider.Reply("Encontrei a contagem atual.",List.of(),false));
        SupportAssistantService siteService=service(siteRepo,true,siteProvider,List.of());
        var siteResult=siteService.send(siteRepo.current.id(),user(20),new SupportDtos.UserMessageRequest("Quantas ONGs existem no site?",UUID.randomUUID().toString()));
        String siteAnswer=siteResult.messages().stream().filter(message->"AI".equals(message.senderType())).reduce((a,b)->b).orElseThrow().body();
        assertTrue(siteAnswer.contains("Foram encontrados 2 resultados."));
        SupportPublicContentSearch siteSearch=(SupportPublicContentSearch)ReflectionTestUtils.getField(siteService,"search");
        verify(siteSearch).count("", "NGO", null, "");
    }

    @Test void missingMaterialPreferenceAsksOneClarificationWithoutEscalating() {
        MemoryRepository repo=new MemoryRepository("AI_ACTIVE",20);
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        when(provider.interpret(any())).thenReturn(new SupportAssistantProvider.Interpretation(List.of(),true,"Em qual cidade você prefere procurar?",false,false,"NONE"));
        when(provider.respond(any())).thenReturn(new SupportAssistantProvider.Reply("Em qual cidade você prefere procurar?",List.of(),false));
        SupportAssistantService service=service(repo,true,provider,List.of());
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest("Quero algo perto",UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE",result.state());
        assertTrue(result.messages().stream().anyMatch(message->"AI".equals(message.senderType())&&message.body().contains("Em qual cidade")));
        assertFalse(repo.events.contains("AI_ACTIVE>WAITING_HUMAN"));
        SupportPublicContentSearch search=(SupportPublicContentSearch)ReflectionTestUtils.getField(service,"search");
        verify(search,never()).search(any(),any());
    }
    @Test void providerFailureStaysInAiConversationAndOffersHonestRetry() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantProvider provider = mock(SupportAssistantProvider.class);
        when(provider.respond(any())).thenThrow(new IllegalStateException("secret provider detail"));
        SupportAssistantService service=service(repo,true,provider,List.of(SOURCE));
        var result=service.send(repo.current.id(),user(20),new SupportDtos.UserMessageRequest("ONG Esperança",UUID.randomUUID().toString()));
        assertEquals("AI_ACTIVE",result.state());
        assertFalse(result.messages().stream().anyMatch(message->message.body().contains("secret provider detail")));
        assertTrue(result.messages().stream().anyMatch(message->message.body().contains("continua nesta conversa")
                && message.body().contains("Tente novamente")));
        assertFalse(repo.events.contains("AI_ACTIVE>WAITING_HUMAN"));
    }

    @Test void userCannotReadAnotherOwnersConversation() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        SupportAssistantService service = service(repo, false, null, List.of());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.mine(repo.current.id(), user(21)));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test void userCanAtomicallyResumeFromWaitingWithoutCallingProviderOrReplayingHistory() {
        MemoryRepository repo=new MemoryRepository("WAITING_HUMAN",20);
        repo.addMessage(repo.current.id(),"USER",20,"Você","Preciso de ajuda humana.","waiting-user");
        repo.addMessage(repo.current.id(),"SYSTEM",null,"Equipe Porto Ajuda","Aguardando atendimento.","waiting-notice");
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        SupportAssistantService service=service(repo,true,provider,List.of(SOURCE));
        var result=service.continueWithSophie(repo.current.id(),user(20));
        assertEquals("AI_ACTIVE",result.state());
        assertNull(result.assignedStaffLabel());
        assertTrue(result.messages().stream().anyMatch(message->message.body().contains("cancelada")));
        assertTrue(repo.events.contains("WAITING_HUMAN>AI_ACTIVE"));
        verifyNoInteractions(provider);
    }

    @Test void userReturningFromHumanActiveNotifiesAssigneeAndIsIdempotent() {
        MemoryRepository repo=new MemoryRepository("HUMAN_ACTIVE",20);
        repo.addMessage(repo.current.id(),"STAFF",99,"Equipe Porto Ajuda","Já conferi a informação.","prior-staff-reply");
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        SupportAssistantService service=service(repo,true,provider,List.of(SOURCE));
        var result=service.continueWithSophie(repo.current.id(),user(20));
        assertEquals("AI_ACTIVE",result.state());
        assertTrue(result.messages().stream().anyMatch(message -> message.senderType().equals("USER")
                && message.body().contains("continuar esta conversa com a Sophie")));
        assertTrue(result.messages().stream().anyMatch(message -> message.senderType().equals("SYSTEM")
                && message.body().contains("equipe foi avisada")));
        int count=repo.messages.size();
        assertEquals("AI_ACTIVE",service.continueWithSophie(repo.current.id(),user(20)).state());
        assertEquals(count,repo.messages.size());
        verifyNoInteractions(provider);
    }

    @Test void userCannotResumeAnotherPersonsWaitingConversation() {
        MemoryRepository repo=new MemoryRepository("WAITING_HUMAN",20);
        SupportAssistantService service=service(repo,true,mock(SupportAssistantProvider.class),List.of(SOURCE));
        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.continueWithSophie(repo.current.id(),user(21)));
        assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode());
        assertEquals("WAITING_HUMAN",repo.current.state());
    }

    @Test void unavailableSophieDoesNotCancelTheHumanQueue() {
        MemoryRepository repo=new MemoryRepository("WAITING_HUMAN",20);
        SupportAssistantService service=service(repo,false,null,List.of());
        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.continueWithSophie(repo.current.id(),user(20)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.getStatusCode());
        assertEquals("WAITING_HUMAN",repo.current.state());
    }

    @Test void concurrentStaffTakeoverAndUserResumeLeaveOneConsistentConversationMode() throws Exception {
        MemoryRepository repo=new MemoryRepository("WAITING_HUMAN",20);
        SupportAssistantProvider provider=mock(SupportAssistantProvider.class);
        AdminAuthorizationService authorization=mock(AdminAuthorizationService.class);
        when(authorization.requirePermission(AdminPermission.SUPPORT_MANAGE))
                .thenReturn(new AdminActor(99,UUID.randomUUID(),AdminProfile.MODERATOR));
        var service=service(repo,true,provider,List.of(SOURCE),authorization);
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> resume=pool.submit(()->{
                ready.countDown(); start.await();
                try { service.continueWithSophie(repo.current.id(),user(20)); return true; }
                catch(ResponseStatusException conflict) {
                    if(conflict.getStatusCode()==HttpStatus.CONFLICT) return false;
                    throw conflict;
                }
            });
            Future<Boolean> takeover=pool.submit(()->{
                ready.countDown(); start.await();
                try { service.takeOver(repo.current.id()); return true; }
                catch(ResponseStatusException conflict) {
                    if(conflict.getStatusCode()==HttpStatus.CONFLICT) return false;
                    throw conflict;
                }
            });
            ready.await(); start.countDown();
            resume.get(); takeover.get();
            assertTrue(List.of("AI_ACTIVE","HUMAN_ACTIVE").contains(repo.current.state()));
            assertEquals("HUMAN_ACTIVE".equals(repo.current.state())?99:null,repo.current.assignedStaffUserId());
            assertFalse(repo.messages.stream().anyMatch(message->message.senderType().equals("AI")));
            verifyNoInteractions(provider);
        } finally { pool.shutdownNow(); }
    }

    @Test void onlyAuthorizedStaffCanClaimAndSecondClaimConflicts() {
        MemoryRepository repo = new MemoryRepository("WAITING_HUMAN", 20);
        AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
        when(authorization.requirePermission(AdminPermission.SUPPORT_MANAGE))
                .thenReturn(new AdminActor(99, UUID.randomUUID(), AdminProfile.MODERATOR));
        when(authorization.userHasPermission(99, AdminPermission.SUPPORT_MANAGE)).thenReturn(true);
        SupportAssistantService service = service(repo, false, null, List.of(), authorization);
        var claimed = service.takeOver(repo.current.id());
        assertEquals("HUMAN_ACTIVE", claimed.state());
        assertEquals("Equipe Porto Ajuda", claimed.assignedStaffLabel());
        assertThrows(ResponseStatusException.class, () -> service.takeOver(repo.current.id()));
        verify(authorization, times(2)).requirePermission(AdminPermission.SUPPORT_MANAGE);
    }

    @Test void staffDirectoryReturnsOnlyOtherProfilesWithSupportManage() {
        MemoryRepository repo = new MemoryRepository("AI_ACTIVE", 20);
        AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
        when(authorization.requirePermission(AdminPermission.SUPPORT_MANAGE))
                .thenReturn(new AdminActor(99, UUID.randomUUID(), AdminProfile.MODERATOR));
        AdminAssignmentRepository assignments = mock(AdminAssignmentRepository.class);
        when(assignments.findActiveSupportCandidates()).thenReturn(List.of(
                new AdminAssignmentRepository.SupportStaffCandidate(99, "Operador atual", AdminProfile.SUPER_ADMIN),
                new AdminAssignmentRepository.SupportStaffCandidate(100, "Atendente Ana", AdminProfile.MODERATOR),
                new AdminAssignmentRepository.SupportStaffCandidate(101, "Auditor", AdminProfile.AUDITOR),
                new AdminAssignmentRepository.SupportStaffCandidate(102, "Administradora", AdminProfile.PLATFORM_ADMIN)));
        SupportAssistantService service = service(repo, false, null, List.of(), authorization, assignments, Clock.systemUTC());
        var candidates = service.eligibleStaff();
        assertEquals(List.of(100, 102), candidates.stream().map(SupportDtos.EligibleStaff::userId).toList());
        assertEquals(List.of("Atendente Ana", "Administradora"), candidates.stream().map(SupportDtos.EligibleStaff::displayName).toList());
    }

    @Test void transferRechecksActiveAssignmentAndSupportManagePermissionAtMutationTime() {
        MemoryRepository repo = new MemoryRepository("HUMAN_ACTIVE", 20);
        AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
        when(authorization.requirePermission(AdminPermission.SUPPORT_MANAGE))
                .thenReturn(new AdminActor(99, UUID.randomUUID(), AdminProfile.MODERATOR));
        AdminAssignmentRepository assignments = mock(AdminAssignmentRepository.class);
        when(assignments.findActiveByUserIdForUpdate(101)).thenReturn(java.util.Optional.empty());
        SupportAssistantService service = service(repo, false, null, List.of(), authorization, assignments, Clock.systemUTC());

        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> service.transfer(
                repo.current.id(), new SupportDtos.TransferRequest(101, "Tentativa sintética de transferência.")));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        assertEquals(99, repo.current.assignedStaffUserId());
        verify(assignments).findActiveByUserIdForUpdate(101);
        verifyNoMoreInteractions(assignments);
    }

    @Test void injectedClockStartsAnewClosureDeadlineAfterReopening() {
        Instant closedAt=Instant.parse("2026-01-01T12:00:00Z");
        MemoryRepository repo=new MemoryRepository("AI_ACTIVE",20);
        Usuario user=user(20);
        SupportAssistantService first=service(repo,false,null,List.of(),mock(AdminAuthorizationService.class),Clock.fixed(closedAt,ZoneOffset.UTC));
        var closed=first.closeMine(repo.current.id(),user);
        assertEquals(closedAt,closed.closedAt());
        assertEquals(closedAt.plus(Duration.ofDays(30)),closed.contentExpiresAt());
        SupportAssistantService reopened=service(repo,false,null,List.of(),mock(AdminAuthorizationService.class),Clock.fixed(closedAt.plus(Duration.ofDays(5)),ZoneOffset.UTC));
        assertNull(reopened.reopen(repo.current.id(),user).closedAt());
        var closedAgain=reopened.closeMine(repo.current.id(),user);
        assertEquals(closedAt.plus(Duration.ofDays(5)),closedAgain.closedAt());
        assertEquals(closedAt.plus(Duration.ofDays(35)),closedAgain.contentExpiresAt());
    }

    private static SupportAssistantService service(MemoryRepository repo, boolean ai,
            SupportAssistantProvider provider, List<SupportDtos.SearchResult> sources) {
        return service(repo, ai, provider, sources, mock(AdminAuthorizationService.class));
    }

    private static SupportAssistantService service(MemoryRepository repo, boolean ai,
            SupportAssistantProvider provider, List<SupportDtos.SearchResult> sources,
            AdminAuthorizationService authorization) {
        return service(repo,ai,provider,sources,authorization,Clock.systemUTC());
    }

    private static SupportAssistantService service(MemoryRepository repo, boolean ai,
            SupportAssistantProvider provider, List<SupportDtos.SearchResult> sources,
            AdminAuthorizationService authorization, Clock clock) {
        return service(repo, ai, provider, sources, authorization, mock(AdminAssignmentRepository.class), clock);
    }

    private static SupportAssistantService service(MemoryRepository repo, boolean ai,
            SupportAssistantProvider provider, List<SupportDtos.SearchResult> sources,
            AdminAuthorizationService authorization, AdminAssignmentRepository assignments, Clock clock) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (provider != null) factory.addBean("supportProvider", provider);
        ObjectProvider<SupportAssistantProvider> providerRef = factory.getBeanProvider(SupportAssistantProvider.class);
        SupportPublicContentSearch search = mock(SupportPublicContentSearch.class);
        when(search.search(any(), any())).thenReturn(sources);
        when(search.search(any(), any(), nullable(com.portoajuda.aplicacao_osc.enums.CausaSocial.class), anyString())).thenReturn(sources);
        when(search.count(any(), any(), nullable(com.portoajuda.aplicacao_osc.enums.CausaSocial.class), anyString()))
                .thenReturn(new SupportDtos.SearchResult("count:test","CONTAGEM","Contagem","Foram encontrados 2 resultados.","/catalogo?categoria=ongs"));
        when(search.help(any())).thenReturn(sources);
        when(search.openPage(any(), any(), any())).thenReturn(sources);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenAnswer(ignored -> new SimpleTransactionStatus());
        doNothing().when(manager).commit(any(TransactionStatus.class));
        doNothing().when(manager).rollback(any(TransactionStatus.class));
        return new SupportAssistantService(repo, search, authorization, assignments,
                providerRef, mock(GeminiRequestBudget.class), manager, true, ai, 12, clock);
    }

    private static Usuario user(int id) { Usuario user=new Usuario(); ReflectionTestUtils.setField(user,"id",id); return user; }

    private static final class MemoryRepository extends SupportConversationRepository {
        private Row current;
        private final List<SupportDtos.Message> messages=new ArrayList<>();
        private final Set<String> keys=new HashSet<>();
        private final List<String> events=new ArrayList<>();
        private long nextMessage=1;
        private MemoryRepository(String state, int owner) {
            super(mock(JdbcTemplate.class));
            Instant now=Instant.now(); current=new Row(UUID.randomUUID(),owner,state,
                    "HUMAN_ACTIVE".equals(state)?99:null,0,now,now,now,now,
                    "CLOSED".equals(state)?now:null,null,false,false);
        }
        @Override public void lockUser(int id) {}
        @Override public int countOpenForUser(int id) { return 0; }
        @Override public int countRecentMessagesForUser(int id, Instant since) { return 0; }
        @Override public int countMessages(UUID id) { return messages.size(); }
        @Override public Row create(UUID id,int owner) { Instant now=Instant.now(); current=new Row(id,owner,"AI_ACTIVE",null,0,now,now,now,now,null,null,false,false); return current; }
        @Override public synchronized java.util.Optional<Row> lock(UUID id) { return current.id().equals(id)?java.util.Optional.of(current):java.util.Optional.empty(); }
        @Override public synchronized java.util.Optional<Row> find(UUID id) { return lock(id); }
        @Override public java.util.Optional<Row> owned(UUID id,int owner) { return lock(id).filter(row -> row.ownerUserId()==owner); }
        @Override public synchronized int transition(UUID id,long version,String from,String to,Integer assignee,Instant occurredAt) {
            if(!current.id().equals(id)||current.version()!=version||!current.state().equals(from))return 0;
            current=new Row(id,current.ownerUserId(),to,assignee,version+1,current.createdAt(),occurredAt,occurredAt,occurredAt,
                    "CLOSED".equals(to)?occurredAt:null,current.contentPurgedAt(),false,false);return 1;
        }
        @Override public synchronized int bumpForUserMessage(UUID id,long version) {
            if(current.version()!=version||"CLOSED".equals(current.state()))return 0;
            Instant now=Instant.now(); current=new Row(id,current.ownerUserId(),current.state(),current.assignedStaffUserId(),version+1,current.createdAt(),now,current.stateChangedAt(),now,current.closedAt(),current.contentPurgedAt(),false,false);return 1;
        }
        @Override public boolean hasMessage(UUID id,String type,Integer userId,String key){return keys.contains(type+":"+userId+":"+key);}
        @Override public synchronized long addMessage(UUID id,String type,Integer userId,String label,String body,String key){
            if(!keys.add(type+":"+userId+":"+key))return messages.stream().filter(m->m.body().equals(body)).mapToLong(SupportDtos.Message::id).findFirst().orElse(-1);
            messages.add(new SupportDtos.Message(nextMessage++,type,label,body,Instant.now()));return nextMessage-1;
        }
        @Override public List<SupportDtos.Message> messages(UUID id,int limit){return List.copyOf(messages);}
        @Override public synchronized void event(UUID id,String actor,Integer userId,String type,String from,String to,String reason){events.add(type.equals("AI_SUPPRESSED")?type:from+">"+to);}
        @Override public void markUserRead(UUID id,int userId){}
        @Override public void markStaffRead(UUID id){}
    }
}
