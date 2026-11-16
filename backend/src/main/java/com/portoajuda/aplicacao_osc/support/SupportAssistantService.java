package com.portoajuda.aplicacao_osc.support;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.GeminiRequestBudget;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupportAssistantService {
    private static final int MAX_MESSAGES = 200;
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private final SupportConversationRepository repository;
    private final SupportPublicContentSearch search;
    private final AdminAuthorizationService adminAuthorization;
    private final AdminAssignmentRepository assignments;
    private final ObjectProvider<SupportAssistantProvider> providers;
    private final GeminiRequestBudget sharedGeminiBudget;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final boolean aiEnabled;
    private final int perMinuteLimit;
    private final Clock clock;
    private final String assistantName;

    @Autowired
    public SupportAssistantService(SupportConversationRepository repository, SupportPublicContentSearch search,
            AdminAuthorizationService adminAuthorization, AdminAssignmentRepository assignments,
            ObjectProvider<SupportAssistantProvider> providers, GeminiRequestBudget sharedGeminiBudget,
            PlatformTransactionManager transactionManager,
            @Value("${app.support-assistant.enabled:false}") boolean enabled,
            @Value("${app.support-assistant.ai.enabled:false}") boolean aiEnabled,
            @Value("${app.support-assistant.rate-limit-per-minute:12}") int perMinuteLimit,
            @Value("${app.support-assistant.display-name:Sophie}") String assistantName) {
        this(repository, search, adminAuthorization, assignments, providers, sharedGeminiBudget,
                transactionManager, enabled, aiEnabled, perMinuteLimit, Clock.systemUTC(), assistantName);
    }

    public SupportAssistantService(SupportConversationRepository repository, SupportPublicContentSearch search,
            AdminAuthorizationService adminAuthorization, AdminAssignmentRepository assignments,
            ObjectProvider<SupportAssistantProvider> providers, GeminiRequestBudget sharedGeminiBudget,
            PlatformTransactionManager transactionManager, boolean enabled, boolean aiEnabled,
            int perMinuteLimit) {
        this(repository, search, adminAuthorization, assignments, providers, sharedGeminiBudget,
                transactionManager, enabled, aiEnabled, perMinuteLimit, Clock.systemUTC(), "Sophie");
    }

    SupportAssistantService(SupportConversationRepository repository, SupportPublicContentSearch search,
            AdminAuthorizationService adminAuthorization, AdminAssignmentRepository assignments,
            ObjectProvider<SupportAssistantProvider> providers, GeminiRequestBudget sharedGeminiBudget,
            PlatformTransactionManager transactionManager, boolean enabled, boolean aiEnabled,
            int perMinuteLimit, Clock clock) {
        this(repository, search, adminAuthorization, assignments, providers, sharedGeminiBudget,
                transactionManager, enabled, aiEnabled, perMinuteLimit, clock, "Sophie");
    }

    SupportAssistantService(SupportConversationRepository repository, SupportPublicContentSearch search,
            AdminAuthorizationService adminAuthorization, AdminAssignmentRepository assignments,
            ObjectProvider<SupportAssistantProvider> providers, GeminiRequestBudget sharedGeminiBudget,
            PlatformTransactionManager transactionManager, boolean enabled, boolean aiEnabled,
            int perMinuteLimit, Clock clock, String assistantName) {
        this.repository = repository; this.search = search; this.adminAuthorization = adminAuthorization;
        this.assignments = assignments; this.providers = providers; this.sharedGeminiBudget = sharedGeminiBudget;
        this.transaction = new TransactionTemplate(transactionManager); this.enabled = enabled;
        this.aiEnabled = aiEnabled; this.perMinuteLimit = Math.max(1, Math.min(60, perMinuteLimit));
        this.clock = clock;
        String safeName = assistantName == null ? "" : assistantName.strip().replaceAll("[\\p{Cntrl}]", "");
        this.assistantName = safeName.isBlank() ? "Sophie" : safeName.substring(0, Math.min(40, safeName.length()));
    }

    public SupportDtos.Intro intro() {
        return new SupportDtos.Intro("Como podemos ajudar?",
                "Encontre orientações sobre o Porto Ajuda e consulte informações públicas de ONGs, publicações e voluntariados.",
                List.of("Orientações do site", "ONGs públicas", "Publicações públicas", "Oportunidades de voluntariado"),
                "Se você iniciar uma conversa persistente, a equipe autorizada poderá ler as mensagens para responder. As respostas humanas são assíncronas e podem levar algum tempo.",
                assistantName, enabled, aiEnabled && providers.getIfAvailable() != null);
    }

    public List<SupportDtos.SearchResult> publicSearch(String query, String category) {
        return search.search(query, category);
    }

    public SupportDtos.Conversation create(Usuario user) {
        int userId = requireUser(user);
        requireEnabled();
        UUID id = UUID.randomUUID();
        SupportConversationRepository.Row row = transaction.execute(status -> {
            repository.lockUser(userId);
            if (repository.countOpenForUser(userId) >= 5) throw conflict("Você já possui várias conversas em andamento. Continue uma conversa existente.");
            return createConversation(id, userId);
        });
        return mine(id, user);
    }

    /** Closes the selected conversation and opens its replacement atomically. The new UUID makes retries idempotent. */
    public SupportDtos.Conversation startNewConversation(SupportDtos.NewConversationRequest request, Usuario user) {
        int userId = requireUser(user); requireEnabled();
        if (request == null || request.conversationId() == null
                || request.conversationId().equals(request.previousConversationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível iniciar uma nova conversa.");
        }
        UUID newId = transaction.execute(status -> {
            repository.lockUser(userId);
            SupportConversationRepository.Row retry = repository.find(request.conversationId()).orElse(null);
            if (retry != null) {
                if (retry.ownerUserId() == null || retry.ownerUserId() != userId) throw notFound();
                return retry.id();
            }
            if (request.previousConversationId() != null) {
                SupportConversationRepository.Row previous = ownedLocked(request.previousConversationId(), userId);
                if (!"CLOSED".equals(previous.state())) {
                    closeOwnedConversation(previous, userId, "Solicitação encerrada ao iniciar uma nova conversa.");
                    repository.addMessage(previous.id(), "SYSTEM", null, "Porto Ajuda",
                            "Esta conversa foi encerrada ao iniciar outra. O histórico permanece disponível até o prazo de retenção.",
                            "new-conversation:" + request.conversationId());
                }
            }
            if (repository.countOpenForUser(userId) >= 5) {
                throw conflict("Você possui várias conversas em andamento. Continue uma conversa existente.");
            }
            createConversation(request.conversationId(), userId);
            return request.conversationId();
        });
        return mine(newId, user);
    }

    private SupportConversationRepository.Row createConversation(UUID id, int userId) {
        SupportConversationRepository.Row created = repository.create(id, userId);
        repository.addMessage(id, "SYSTEM", null, assistantName + " · mensagem automática",
                "Olá! Sou a " + assistantName + ", assistente virtual do Porto Ajuda. Como posso ajudar?", "welcome:assistant:v1");
        return created;
    }

    public List<SupportDtos.ConversationSummary> mine(Usuario user) {
        int userId = requireUser(user); requireEnabled();
        return repository.listMine(userId, 30);
    }

    public List<SupportDtos.EligibleStaff> eligibleStaff() {
        AdminActor actor = adminAuthorization.requirePermission(AdminPermission.SUPPORT_MANAGE);
        requireEnabled();
        return assignments.findActiveSupportCandidates().stream()
                .filter(candidate -> !candidate.userId().equals(actor.userId()))
                .filter(candidate -> com.portoajuda.aplicacao_osc.admin.AdminPermissionCatalog
                        .grants(candidate.profile(), AdminPermission.SUPPORT_MANAGE))
                .map(candidate -> new SupportDtos.EligibleStaff(candidate.userId(), candidate.displayName()))
                .toList();
    }

    public SupportDtos.Conversation mine(UUID id, Usuario user) {
        int userId = requireUser(user); requireEnabled();
        SupportConversationRepository.Row row = repository.owned(id, userId)
                .orElseThrow(() -> notFound());
        transaction.executeWithoutResult(status -> repository.markUserRead(id, userId));
        return detail(repository.find(id).orElseThrow(SupportAssistantService::notFound), true);
    }

    public SupportDtos.Conversation reopen(UUID id, Usuario user) {
        int userId = requireUser(user); requireEnabled();
        SupportConversationRepository.Row row = transaction.execute(status -> {
            repository.lockUser(userId);
            SupportConversationRepository.Row locked = ownedLocked(id, userId);
            if (!"CLOSED".equals(locked.state())) return locked;
            if (repository.countOpenForUser(userId) >= 5) throw conflict("Você já possui várias conversas em andamento. Encerre uma conversa existente antes de reabrir outra.");
            if (repository.transition(id, locked.version(), "CLOSED", "AI_ACTIVE", null, clock.instant()) != 1) throw conflict("A conversa foi alterada. Atualize e tente novamente.");
            repository.event(id, "USER", userId, "STATE_CHANGED", "CLOSED", "AI_ACTIVE", "Pessoa solicitante reabriu a conversa.");
            repository.addMessage(id, "SYSTEM", null, "Porto Ajuda", "Conversa reaberta. Você pode solicitar atendimento humano; mensagens antigas não serão reenviadas à Sophie.", "reopen:" + (locked.version() + 1));
            return repository.lock(id).orElseThrow();
        });
        return mine(id, user);
    }

    public SupportDtos.Conversation requestHuman(UUID id, Usuario user, SupportDtos.ActionRequest request) {
        int userId = requireUser(user); requireEnabled();
        String reason = bounded(request == null ? "" : request.reason(), 500, false);
        SupportConversationRepository.Row row = transaction.execute(status -> {
            SupportConversationRepository.Row locked = ownedLocked(id, userId);
            if ("CLOSED".equals(locked.state())) throw conflict("Esta conversa está encerrada. Reabra-a para continuar.");
            if ("HUMAN_ACTIVE".equals(locked.state()) || "WAITING_HUMAN".equals(locked.state())) return locked;
            if (repository.transition(id, locked.version(), locked.state(), "WAITING_HUMAN", null, clock.instant()) != 1) throw conflict("A conversa foi alterada. Atualize e tente novamente.");
            repository.event(id, "USER", userId, "STATE_CHANGED", locked.state(), "WAITING_HUMAN", reason);
            repository.addMessage(id, "SYSTEM", null, "Porto Ajuda", "Sua conversa foi encaminhada para a equipe. As respostas são assíncronas; você pode enviar mais informações enquanto aguarda.", "human-request:" + (locked.version() + 1));
            return repository.lock(id).orElseThrow();
        });
        return mine(id, user);
    }

    /** The user may atomically leave a pending or active staff handoff; a human assignee is notified in the history. */
    public SupportDtos.Conversation continueWithSophie(UUID id, Usuario user) {
        int userId = requireUser(user); requireEnabled();
        if (!aiEnabled || providers.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A Sophie está temporariamente indisponível. A solicitação humana continua ativa.");
        }
        transaction.execute(status -> {
            repository.lockUser(userId);
            SupportConversationRepository.Row locked = ownedLocked(id, userId);
            if ("AI_ACTIVE".equals(locked.state())) return locked;
            if (!List.of("WAITING_HUMAN", "HUMAN_ACTIVE").contains(locked.state())) {
                throw conflict("Esta conversa não pode voltar para a Sophie neste estado.");
            }
            String previousState = locked.state();
            if (repository.transition(id, locked.version(), previousState, "AI_ACTIVE", null, clock.instant()) != 1) {
                throw conflict("A conversa foi alterada. Atualize e tente novamente.");
            }
            repository.event(id, "USER", userId, "STATE_CHANGED", previousState, "AI_ACTIVE",
                    "Pessoa solicitante escolheu continuar com a Sophie.");
            if ("HUMAN_ACTIVE".equals(previousState)) {
                repository.addMessage(id, "USER", userId, "Você",
                        "Quero continuar esta conversa com a Sophie.", "resume-ai:" + (locked.version() + 1));
                repository.addMessage(id, "SYSTEM", null, "Aviso automático",
                        "O atendimento humano foi encerrado a pedido da pessoa solicitante; a equipe foi avisada.",
                        "resume-ai-notice:" + (locked.version() + 1));
            } else {
                repository.addMessage(id, "SYSTEM", null, assistantName + " · aviso automático",
                        "A solicitação de atendimento humano foi cancelada. A Sophie continuará a conversa sem repetir mensagens anteriores.",
                        "resume-ai-notice:" + (locked.version() + 1));
            }
            return repository.lock(id).orElseThrow();
        });
        return mine(id, user);
    }

    public SupportDtos.Conversation send(UUID id, Usuario user, SupportDtos.UserMessageRequest request) {
        int userId = requireUser(user); requireEnabled();
        String body = bounded(request == null ? null : request.body(), MAX_MESSAGE_LENGTH, true);
        String key = idempotencyKey(request == null ? null : request.idempotencyKey());
        SendSnapshot snapshot = transaction.execute(status -> {
            repository.lockUser(userId);
            SupportConversationRepository.Row locked = ownedLocked(id, userId);
            if ("CLOSED".equals(locked.state())) throw conflict("Esta conversa está encerrada. Reabra-a antes de enviar mensagem.");
            if (repository.hasMessage(id, "USER", userId, key)) return new SendSnapshot(locked, false);
            if (repository.countRecentMessagesForUser(userId, clock.instant().minus(Duration.ofMinutes(1))) >= perMinuteLimit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Você enviou mensagens rapidamente. Aguarde um minuto e tente novamente.");
            }
            if (repository.countMessages(id) >= MAX_MESSAGES) throw conflict("Esta conversa atingiu o limite de mensagens. Peça à equipe que a encerre para continuar em uma nova conversa.");
            repository.addMessage(id, "USER", userId, "Você", body, key);
            if (repository.bumpForUserMessage(id, locked.version()) != 1) throw conflict("A conversa foi alterada. Atualize antes de enviar novamente.");
            return new SendSnapshot(repository.lock(id).orElseThrow(), true);
        });
        if (!snapshot.inserted()) return mine(id, user);
        if (!"AI_ACTIVE".equals(snapshot.row().state())) return mine(id, user);
        if (explicitHumanRequest(body)) {
            escalateForHumanRequest(id, snapshot.row().version(),
                    "Sua solicitação foi encaminhada para uma pessoa da equipe. As respostas são assíncronas e você pode acompanhar esta conversa aqui.");
            return mine(id, user);
        }
        if (outsideInitialScope(body) && !safeAccountNavigation(body)) {
            addAiNotice(id, snapshot.row().version(), "scope-limit:" + snapshot.row().version(),
                    "Posso orientar sobre os caminhos seguros, mas não consulto dados privados nem faço alterações de conta, conteúdo ou operações financeiras. Use os recursos próprios da plataforma; o atendimento humano também não contorna essas permissões.");
            return mine(id, user);
        }
        if (!aiEnabled || providers.getIfAvailable() == null) {
            addAiNotice(id, snapshot.row().version(), "ai-disabled:" + snapshot.row().version(),
                    "A Sophie está temporariamente indisponível. Sua mensagem permaneceu nesta conversa. Você pode tentar novamente mais tarde ou pedir atendimento humano.");
            return mine(id, user);
        }
        return generateReply(id, snapshot.row(), body, user);
    }

    /** Explicit user retry; the database message key makes each failed user message retryable at most once. */
    public SupportDtos.Conversation retryAi(UUID id, Usuario user) {
        int userId = requireUser(user); requireEnabled();
        if (!aiEnabled || providers.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A Sophie está temporariamente indisponível. Você pode tentar mais tarde ou pedir atendimento humano.");
        }
        RetrySnapshot retry = transaction.execute(status -> {
            repository.lockUser(userId);
            SupportConversationRepository.Row locked = ownedLocked(id, userId);
            if (!"AI_ACTIVE".equals(locked.state())) throw conflict("A conversa não está disponível para uma nova tentativa da Sophie.");
            SupportDtos.Message latestUser = repository.messages(id, MAX_MESSAGES).stream()
                    .filter(message -> "USER".equals(message.senderType())).reduce((first, second) -> second)
                    .orElseThrow(() -> conflict("Não há uma mensagem para tentar novamente."));
            String key = "ai-retry:" + latestUser.id();
            if (repository.hasMessage(id, "SYSTEM", null, key)
                    || repository.hasMessage(id, "AI", null, "ai-reply:" + locked.version())) {
                return new RetrySnapshot(locked, latestUser.body(), false);
            }
            repository.addMessage(id, "SYSTEM", null, assistantName + " · aviso automático",
                    "Vou tentar responder à sua última mensagem novamente.", key);
            return new RetrySnapshot(locked, latestUser.body(), true);
        });
        if (!retry.claimed()) return mine(id, user);
        return generateReply(id, retry.row(), retry.question(), user);
    }

    private SupportDtos.Conversation generateReply(UUID id, SupportConversationRepository.Row snapshot,
            String body, Usuario user) {
        List<SupportDtos.Message> history = historyBeforeLatestQuestion(id, body);
        try {
            SupportAssistantProvider provider = providers.getObject();
            SupportAssistantProvider.Interpretation interpretation = provider.interpret(
                    new SupportAssistantProvider.InterpretationInput(minimizePrivateLocation(body), history));
            if (interpretation == null) interpretation = compatibilityInterpretation(body);
            if (interpretation.explicitHumanRequest()) {
                escalateForHumanRequest(id, snapshot.version(),
                        "Sua solicitação foi encaminhada para a equipe. As respostas são assíncronas; você pode acompanhar esta conversa aqui.");
                return mine(id, user);
            }
            List<SupportDtos.SearchResult> sources = new ArrayList<>();
            List<SupportAssistantProvider.CapabilityOutcome> outcomes = new ArrayList<>();
            boolean publicSearchUnavailable = false;
            boolean publicSearchAttempted = false;
            if (!interpretation.privacyBoundary()) {
                for (SupportAssistantProvider.CapabilityCall action : interpretation.capabilities()) {
                    if ("SEARCH_PUBLIC".equals(action.operation()) && validCategory(action.category()) && !action.query().isBlank()) {
                        publicSearchAttempted = true;
                        com.portoajuda.aplicacao_osc.enums.CausaSocial cause = parseCause(action.cause());
                        if(cause==null)cause=SupportPublicContentSearch.inferCurrentTurnCause(body);
                        String query=searchQuery(action,cause);
                        try {
                            List<SupportDtos.SearchResult> found=cause == null && action.city().isBlank()
                                ? search.search(query, action.category())
                                : search.search(query, action.category(), cause, action.city());
                            outcomes.add(new SupportAssistantProvider.CapabilityOutcome("SEARCH_PUBLIC",action.category(),
                                    found.isEmpty()?"NO_MATCHES":"RESULTS",found.size()));
                            sources.addAll(found.stream().limit(3).toList());
                        } catch (RuntimeException unavailable) {
                            publicSearchUnavailable = true;
                            outcomes.add(new SupportAssistantProvider.CapabilityOutcome("SEARCH_PUBLIC",action.category(),"UNAVAILABLE",0));
                            break;
                        }
                    } else if ("COUNT_PUBLIC".equals(action.operation()) && validCategory(action.category())) {
                        publicSearchAttempted = true;
                        try {
                            if ("PREVIOUS_RESULTS".equals(action.scope())) {
                                int previous=countPreviousShownOptions(id);
                                sources.add(new SupportDtos.SearchResult("count:previous","CONTAGEM","Contagem dos resultados anteriores",
                                        "Na resposta anterior foram apresentadas "+previous+" opções públicas.",""));
                                outcomes.add(new SupportAssistantProvider.CapabilityOutcome("COUNT_PUBLIC",action.category(),"COUNTED",previous));
                            } else {
                                com.portoajuda.aplicacao_osc.enums.CausaSocial cause=parseCause(action.cause());
                                if(cause==null)cause=SupportPublicContentSearch.inferCurrentTurnCause(body);
                                sources.add(search.count(searchQuery(action,cause),action.category(),cause,action.city()));
                                outcomes.add(new SupportAssistantProvider.CapabilityOutcome("COUNT_PUBLIC",action.category(),"COUNTED",-1));
                            }
                        } catch (RuntimeException unavailable) {
                            publicSearchUnavailable = true;
                            outcomes.add(new SupportAssistantProvider.CapabilityOutcome("COUNT_PUBLIC",action.category(),"UNAVAILABLE",0));
                            break;
                        }
                    } else if ("SITE_GUIDANCE".equals(action.operation())) {
                        sources.addAll(search.help(action.topic()));
                    } else if ("OPEN_PAGE".equals(action.operation())) {
                        sources.addAll(search.openPage(action.category(), action.cause(), action.topic()));
                    }
                    if (sources.size() >= 5) break;
                }
            }
            sources = sources.stream().collect(java.util.stream.Collectors.toMap(SupportDtos.SearchResult::id,
                    source -> source, (first, ignored) -> first, java.util.LinkedHashMap::new)).values().stream().limit(5).toList();
            SupportAssistantProvider.Reply reply = provider.respond(new SupportAssistantProvider.Input(id,
                    minimizePrivateLocation(body), history, sources, publicSearchUnavailable, interpretation, outcomes));
            String answer = removeUnverifiedLinks(bounded(reply == null ? null : reply.answer(), 2400, true));
            answer = removeCatalogDeflection(answer, sources);
            if (answer.isBlank()) {
                answer = publicSearchUnavailable
                        ? "Não consegui consultar as informações públicas agora. Você pode tentar novamente ou me dizer qual tipo de conteúdo procura."
                        : sources.stream().anyMatch(source -> List.of("ONG", "PUBLICAÇÃO", "VOLUNTARIADO").contains(source.category()))
                                ? "Encontrei opções públicas que podem corresponder ao que você procura."
                                : publicSearchAttempted && sources.isEmpty()
                                        ? "Não encontrei correspondências públicas para esta busca. Se você disser a cidade, causa ou tipo de conteúdo, posso refinar a pesquisa."
                                        : "Posso ajudar a explorar ONGs, publicações e oportunidades públicas. Qual desses assuntos você quer ver?";
            }
            answer = appendVerifiedPublicOptions(answer, sources, interpretation);
            if (reply.requestsHuman()) answer = answer + "\n\nSe quiser que a equipe acompanhe o caso, você pode escolher ‘Falar com uma pessoa’.";
            String saved = appendValidatedHelpLinks(answer, sources, reply == null ? null : reply.sourceIds());
            SupportConversationRepository.Row completedRow = transaction.execute(status -> {
                SupportConversationRepository.Row current = repository.lock(id).orElseThrow(SupportAssistantService::notFound);
                if (!"AI_ACTIVE".equals(current.state()) || current.version() != snapshot.version()) {
                    repository.event(id, "SYSTEM", null, "AI_SUPPRESSED", "AI_ACTIVE", current.state(), "Resposta automatizada descartada após alteração concorrente da conversa.");
                    return current;
                }
                repository.addMessage(id, "AI", null, assistantName + " · resposta automática", saved, "ai-reply:" + snapshot.version());
                return current;
            });
            return mine(id, user);
        } catch (RuntimeException failure) {
            addAiNotice(id, snapshot.version(), "ai-unavailable:" + snapshot.version(),
                    "A Sophie não conseguiu responder agora. Sua mensagem continua nesta conversa. Tente novamente ou escolha ‘Falar com uma pessoa’ se preferir atendimento humano.");
            return mine(id, user);
        }
    }

    static String appendVerifiedPublicOptions(String answer, List<SupportDtos.SearchResult> sources,
            SupportAssistantProvider.Interpretation interpretation) {
        List<SupportDtos.SearchResult> options=sources.stream()
                .filter(source->List.of("ONG","PUBLICAÇÃO","VOLUNTARIADO").contains(source.category())).limit(3).toList();
        if(options.isEmpty())return answer;
        StringBuilder result=new StringBuilder(answer.trim());
        boolean needsList=options.stream().anyMatch(option->!SupportPublicContentSearch.normalize(answer)
                .contains(SupportPublicContentSearch.normalize(option.title())));
        if(needsList)result.append("\n\nOpções relacionadas:");
        for(SupportDtos.SearchResult option:options) {
            String title=plainMarkdown(option.title());
            String summary=plainMarkdown(option.summary());
            String label="ONG".equals(option.category())?"Ver perfil de ":"VOLUNTARIADO".equals(option.category())?"Ver oportunidade: ":"Ver publicação: ";
            String normalized=SupportPublicContentSearch.normalize(result.toString());
            if(normalized.contains(SupportPublicContentSearch.normalize(title))) {
                int occurrence=result.indexOf(title);
                if(occurrence<0)occurrence=result.toString().toLowerCase(java.util.Locale.ROOT).indexOf(title.toLowerCase(java.util.Locale.ROOT));
                if(occurrence>=0)result.replace(occurrence,occurrence+title.length(),"["+title+"]("+option.href()+")");
            } else result.append("\n• ").append(label).append("[").append(title).append("](").append(option.href()).append(") — ").append(summary);
        }
        String normalizedAnswer=SupportPublicContentSearch.normalize(answer);
        boolean hasNextStep=normalizedAnswer.contains("compar")
                ||normalizedAnswer.matches("(?s).*(filtrar|refinar).{0,50}(cidade|bairro|causa).*")
                ||normalizedAnswer.matches("(?s).*(abrir|ver|conhecer|abrisse).{0,50}(perfil|opcao|organizacao).*")
                ||normalizedAnswer.contains("quer que eu");
        if(options.size()>1&&!hasNextStep)result.append("\nSe quiser, posso comparar essas opções ou filtrar por cidade.");
        if(!hasNextStep&&interpretation!=null)interpretation.capabilities().stream()
                .filter(action->"SEARCH_PUBLIC".equals(action.operation()))
                .filter(action->"NGO".equals(action.category())||"PUBLICATION".equals(action.category()))
                .findFirst().ifPresent(action->{
                    SupportPublicContentSearch.CatalogSearchDraft draft=SupportPublicContentSearch.catalogSearchDraft(
                            action.query()+" "+(action.cause()==null||"NONE".equals(action.cause())?"":action.cause().toLowerCase(java.util.Locale.ROOT).replace('_',' ')),action.category());
                    if(draft!=null)result.append("\nBusca editável — causa pré-selecionada: ").append(draft.filterLabel())
                            .append(" [Abrir filtro](").append(draft.href()).append(")");
                });
        return result.toString();
    }
    private static String plainMarkdown(String value) {
        return value==null?"":value.replace("[","［").replace("]","］").replace("(","（").replace(")","）")
                .replace("\r"," ").replace("\n"," ").replace("\t"," ").trim();
    }    private static String removeCatalogDeflection(String answer, List<SupportDtos.SearchResult> sources) {
        if (sources == null || sources.stream().noneMatch(source -> List.of("ONG", "PUBLICAÇÃO", "VOLUNTARIADO").contains(source.category()))) return answer;
        String cleaned = java.util.Arrays.stream(answer.split("(?<=[.!?])\\s+|\\n"))
                .filter(sentence -> !isCatalogDeflection(sentence) && !isUnsupportedTrustEndorsement(sentence))
                .reduce((first, next) -> first + " " + next).orElse("").trim();
        return cleaned.isBlank() && isUnsupportedTrustEndorsement(answer)
                ? "A presença na plataforma não confirma confiabilidade, auditoria ou segurança para doar."
                : cleaned;
    }

    private static boolean validCategory(String category) {
        return List.of("ALL", "NGO", "PUBLICATION", "VOLUNTEERING").contains(category);
    }

    private static com.portoajuda.aplicacao_osc.enums.CausaSocial parseCause(String cause) {
        try { return cause == null || cause.isBlank() || "NONE".equals(cause) ? null
                : com.portoajuda.aplicacao_osc.enums.CausaSocial.valueOf(cause); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private static String searchQuery(SupportAssistantProvider.CapabilityCall action,com.portoajuda.aplicacao_osc.enums.CausaSocial inferredCause) {
        String query=action.query()==null?"":action.query().strip();
        String cause=action.cause()==null||"NONE".equals(action.cause())
                ?(inferredCause==null?"":inferredCause.name().toLowerCase(java.util.Locale.ROOT).replace('_',' '))
                :action.cause().toLowerCase(java.util.Locale.ROOT).replace('_',' ');
        if(!cause.isBlank()&&!SupportPublicContentSearch.normalize(query).contains(SupportPublicContentSearch.normalize(cause))) query=(query+" "+cause).trim();
        return query.substring(0,Math.min(query.length(),120));
    }

    private int countPreviousShownOptions(UUID id) {
        SupportDtos.Message prior=repository.messages(id,MAX_MESSAGES).stream().filter(message->"AI".equals(message.senderType()))
                .reduce((first,second)->second).orElse(null);
        if(prior==null)return 0;
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(?m)^• .*?\\]\\(/(?:ong-profile|catalogo)\\?[^)]+\\)").matcher(prior.body());
        int count=0; while(matcher.find())count++;
        return count;
    }

    private static SupportAssistantProvider.Interpretation compatibilityInterpretation(String body) {
        if (conversationalExchange(body)) return new SupportAssistantProvider.Interpretation(List.of(), false, "", false, false, "NONE");
        String query = body == null ? "" : body.substring(0, Math.min(body.length(), 120));
        String category = SupportPublicContentSearch.inferCategory(query);
        var action = new SupportAssistantProvider.CapabilityCall("SEARCH_PUBLIC", category, query, "NONE", "", "NONE", "CURRENT_QUERY");
        return new SupportAssistantProvider.Interpretation(List.of(action), false, "", false, false, "NONE");
    }
    private static String appendValidatedHelpLinks(String answer, List<SupportDtos.SearchResult> sources, List<String> citedIds) {
        if (sources == null || sources.isEmpty()) return answer;
        StringBuilder output=new StringBuilder(answer);
        sources.stream().filter(source->"CONTAGEM".equals(source.category())).findFirst().ifPresent(count->{
            if(!SupportPublicContentSearch.normalize(answer).contains(SupportPublicContentSearch.normalize(count.summary())))
                output.append("\n\n").append(count.summary());
        });
        if(citedIds==null||citedIds.isEmpty())return output.toString();
        List<SupportDtos.SearchResult> links = sources.stream().filter(source -> citedIds.contains(source.id()))
                .filter(source -> !List.of("ONG", "PUBLICAÇÃO", "VOLUNTARIADO").contains(source.category()))
                .filter(source -> isUsefulInternalHref(source.href())).limit(3).toList();
        if (links.isEmpty()) return output.toString();
        return output + "\n\n" + links.stream().map(source -> "[" + source.title() + "](" + source.href() + ")")
                .reduce((first, next) -> first + "\n" + next).orElse("");
    }
    static List<String> validatedSourceLinks(List<SupportDtos.SearchResult> sources, List<String> citedIds) {
        if (sources == null || sources.isEmpty()) return List.of();
        java.util.LinkedHashSet<String> links = new java.util.LinkedHashSet<>();
        boolean hasContentResults = sources.stream()
                .anyMatch(source -> List.of("ONG", "PUBLICAÇÃO", "VOLUNTARIADO").contains(source.category()));
        for (SupportDtos.SearchResult source : sources) {
            boolean modelCited = citedIds != null && citedIds.contains(source.id());
            boolean resultCard = hasContentResults
                    && List.of("ONG", "PUBLICAÇÃO", "VOLUNTARIADO").contains(source.category());
            if ((modelCited || resultCard) && isUsefulInternalHref(source.href())) links.add(source.href());
            if (links.size() == 3) break;
        }
        return List.copyOf(links);
    }

    private static boolean isUsefulInternalHref(String href) {
        return href != null && href.startsWith("/") && !href.equals("/") && !href.startsWith("//")
                && !href.contains("\\") && !href.matches("(?i)^/https?:.*");
    }

    private static String removeUnverifiedLinks(String answer) {
        return answer.replaceAll("(?i)\\[[^\\]]{1,120}\\]\\((?:https?://|www\\.|/)[^)]+\\)", "")
                .replaceAll("(?i)https?://\\S+|www\\.\\S+", "")
                .replaceAll("(?i)(?<![\\w])/(?:catalogo|ong-profile|oscs|perfil|login)(?:\\?[^\\s),.;]+|#[^\\s),.;]+)?", "")
                .replaceAll("(?m)^\\s*/\\s*$", "")
                .replaceAll("(?m)^[ \\t]*$", "").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static boolean isCatalogDeflection(String answer) {
        String normalized = SupportPublicContentSearch.normalize(answer);
        return normalized.contains("catalogo")
                || normalized.contains("filtro")
                || normalized.matches("(?s).*(va|acesse|abra|procure|busque|pesquise|filtre).{0,60}(site|pagina inicial).*" )
                || normalized.matches("(?s).*(voce pode|e so|basta).{0,60}(filtrar|procurar|buscar).{0,60}.*" );
    }

    private static boolean isUnsupportedTrustEndorsement(String answer) {
        String normalized = SupportPublicContentSearch.normalize(answer);
        return normalized.matches("(?s).*(e|sao|parece|considerada|considerado).{0,45}(confiavel|auditada|auditado|a melhor|segura para doar|seguro para doar).*" );
    }

    public SupportDtos.Conversation closeMine(UUID id, Usuario user) {
        int userId = requireUser(user); requireEnabled();
        SupportConversationRepository.Row row = transaction.execute(status -> {
            SupportConversationRepository.Row locked = ownedLocked(id, userId);
            if ("CLOSED".equals(locked.state())) return locked;
            closeOwnedConversation(locked, userId, "Conversa encerrada pela pessoa solicitante.");
            return repository.lock(id).orElseThrow();
        });
        return mine(id, user);
    }

    public SupportDtos.Page staffQueue(String filter, int page, int size) {
        adminAuthorization.requirePermission(AdminPermission.SUPPORT_READ); requireEnabled();
        String state = filter == null || filter.isBlank() ? "WAITING_HUMAN" : filter.toUpperCase(Locale.ROOT);
        if (!List.of("WAITING_HUMAN", "HUMAN_ACTIVE", "CLOSED", "ALL").contains(state)) throw new IllegalArgumentException("Filtro de atendimento inválido.");
        int safePage = Math.max(0, Math.min(page, 10000)); int safeSize = Math.max(1, Math.min(size, 50));
        List<SupportDtos.ConversationSummary> items = repository.listStaff(state, safeSize + 1, safePage * safeSize);
        return new SupportDtos.Page(items.stream().limit(safeSize).toList(), safePage, safeSize, items.size() > safeSize);
    }

    public SupportDtos.Conversation staffDetail(UUID id) {
        adminAuthorization.requirePermission(AdminPermission.SUPPORT_READ); requireEnabled();
        SupportConversationRepository.Row row = repository.find(id).orElseThrow(SupportAssistantService::notFound);
        transaction.executeWithoutResult(status -> repository.markStaffRead(id));
        return detail(repository.find(id).orElseThrow(SupportAssistantService::notFound), false);
    }

    public SupportDtos.Conversation takeOver(UUID id) {
        AdminActor actor = adminAuthorization.requirePermission(AdminPermission.SUPPORT_MANAGE); requireEnabled();
        SupportConversationRepository.Row row = transaction.execute(status -> {
            SupportConversationRepository.Row locked = repository.lock(id).orElseThrow(SupportAssistantService::notFound);
            boolean staleAssignee = "HUMAN_ACTIVE".equals(locked.state()) && locked.assignedStaffUserId() != null
                    && !adminAuthorization.userHasPermission(locked.assignedStaffUserId(), AdminPermission.SUPPORT_MANAGE);
            if (!List.of("AI_ACTIVE", "WAITING_HUMAN").contains(locked.state()) && !staleAssignee) throw conflict("A conversa não está disponível para assumir.");
            if (repository.transition(id, locked.version(), locked.state(), "HUMAN_ACTIVE", actor.userId(), clock.instant()) != 1) throw conflict("Outro atendente assumiu a conversa. Atualize a fila.");
            repository.assignmentEvent(id, "STAFF", actor.userId(), "CLAIMED", locked.state(), "HUMAN_ACTIVE",
                    "Atendimento assumido.", locked.assignedStaffUserId(), actor.userId());
            repository.addMessage(id, "SYSTEM", null, "Equipe Porto Ajuda", "Uma pessoa da equipe assumiu o atendimento.", "takeover:" + (locked.version() + 1));
            return repository.lock(id).orElseThrow();
        });
        return staffDetail(id);
    }

    public SupportDtos.Conversation staffReply(UUID id, SupportDtos.ReplyRequest request) {
        AdminActor actor = adminAuthorization.requirePermission(AdminPermission.SUPPORT_MANAGE); requireEnabled();
        String body = bounded(request == null ? null : request.body(), MAX_MESSAGE_LENGTH, true);
        String key = idempotencyKey(request == null ? null : request.idempotencyKey());
        SupportConversationRepository.Row row = transaction.execute(status -> {
            SupportConversationRepository.Row locked = repository.lock(id).orElseThrow(SupportAssistantService::notFound);
            if (!"HUMAN_ACTIVE".equals(locked.state()) || !actor.userId().equals(locked.assignedStaffUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas a pessoa responsável pelo atendimento pode responder.");
            if (!repository.hasMessage(id, "STAFF", actor.userId(), key)) {
                repository.addMessage(id, "STAFF", actor.userId(), "Equipe Porto Ajuda", body, key);
                repository.bumpForUserMessage(id, locked.version());
                repository.event(id, "STAFF", actor.userId(), "MESSAGE_SENT", locked.state(), locked.state(), null);
            }
            return repository.lock(id).orElseThrow();
        });
        return staffDetail(id);
    }

    public SupportDtos.Conversation transfer(UUID id, SupportDtos.TransferRequest request) {
        AdminActor actor = adminAuthorization.requirePermission(AdminPermission.SUPPORT_MANAGE); requireEnabled();
        if (request == null || request.targetStaffUserId() == null || request.targetStaffUserId() <= 0) throw new IllegalArgumentException("Selecione uma pessoa atendente válida.");
        String reason = bounded(request.reason(), 1000, true);
        SupportConversationRepository.Row row = transaction.execute(status -> {
            SupportConversationRepository.Row locked = repository.lock(id).orElseThrow(SupportAssistantService::notFound);
            if (!"HUMAN_ACTIVE".equals(locked.state())) throw conflict("Somente atendimentos assumidos podem ser transferidos.");
            if (!assignedIsManageableBy(actor.userId(), locked.assignedStaffUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente a pessoa responsável pode transferir este atendimento.");
            var targetAssignment = assignments.findActiveByUserIdForUpdate(request.targetStaffUserId());
            if (request.targetStaffUserId().equals(actor.userId()) || targetAssignment.isEmpty()
                    || !assignments.lockEligibleActiveAdminAccount(request.targetStaffUserId())
                    || !com.portoajuda.aplicacao_osc.admin.AdminPermissionCatalog.grants(
                            targetAssignment.map(com.portoajuda.aplicacao_osc.admin.AdminAssignment::profile).orElse(null),
                            AdminPermission.SUPPORT_MANAGE)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A pessoa selecionada não está habilitada para atendimentos.");
            }
            Integer previous = locked.assignedStaffUserId();
            if (repository.transition(id, locked.version(), "HUMAN_ACTIVE", "HUMAN_ACTIVE", request.targetStaffUserId(), clock.instant()) != 1) throw conflict("O atendimento foi alterado. Atualize e tente novamente.");
            repository.assignmentEvent(id, "STAFF", actor.userId(), "TRANSFERRED", "HUMAN_ACTIVE", "HUMAN_ACTIVE",
                    reason, previous, request.targetStaffUserId());
            repository.addMessage(id, "SYSTEM", null, "Equipe Porto Ajuda", "O atendimento foi transferido para outra pessoa da equipe.", "transfer:" + (locked.version() + 1));
            return repository.lock(id).orElseThrow();
        });
        return staffDetail(id);
    }

    public SupportDtos.Conversation returnToAi(UUID id, SupportDtos.ActionRequest request) {
        AdminActor actor = adminAuthorization.requirePermission(AdminPermission.SUPPORT_MANAGE); requireEnabled();
        String reason = bounded(request == null ? null : request.reason(), 1000, true);
        SupportConversationRepository.Row row = transaction.execute(status -> {
            SupportConversationRepository.Row locked = repository.lock(id).orElseThrow(SupportAssistantService::notFound);
            if (!"HUMAN_ACTIVE".equals(locked.state()) || !assignedIsManageableBy(actor.userId(), locked.assignedStaffUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente a pessoa responsável pode devolver a conversa à assistência.");
            if (repository.transition(id, locked.version(), "HUMAN_ACTIVE", "AI_ACTIVE", null, clock.instant()) != 1) throw conflict("O atendimento foi alterado. Atualize e tente novamente.");
            repository.assignmentEvent(id, "STAFF", actor.userId(), "STATE_CHANGED", "HUMAN_ACTIVE", "AI_ACTIVE",
                    reason, locked.assignedStaffUserId(), null);
            repository.addMessage(id, "SYSTEM", null, assistantName + " · aviso automático", "A conversa voltou para a " + assistantName + ". Ela responderá somente a novas mensagens; o histórico anterior não será reenviado.", "return-ai:" + (locked.version() + 1));
            return repository.lock(id).orElseThrow();
        });
        return staffDetail(id);
    }

    public SupportDtos.Conversation closeByStaff(UUID id, SupportDtos.ActionRequest request) {
        AdminActor actor = adminAuthorization.requirePermission(AdminPermission.SUPPORT_MANAGE); requireEnabled();
        String reason = bounded(request == null ? null : request.reason(), 1000, true);
        SupportConversationRepository.Row row = transaction.execute(status -> {
            SupportConversationRepository.Row locked = repository.lock(id).orElseThrow(SupportAssistantService::notFound);
            if ("CLOSED".equals(locked.state())) return locked;
            if ("HUMAN_ACTIVE".equals(locked.state()) && !assignedIsManageableBy(actor.userId(), locked.assignedStaffUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente a pessoa responsável pode encerrar este atendimento.");
            if (repository.transition(id, locked.version(), locked.state(), "CLOSED", null, clock.instant()) != 1) throw conflict("O atendimento foi alterado. Atualize e tente novamente.");
            repository.assignmentEvent(id, "STAFF", actor.userId(), "STATE_CHANGED", locked.state(), "CLOSED",
                    reason, locked.assignedStaffUserId(), null);
            repository.addMessage(id, "SYSTEM", null, "Equipe Porto Ajuda", "O atendimento foi encerrado pela equipe.", "staff-close:" + (locked.version() + 1));
            return repository.lock(id).orElseThrow();
        });
        return staffDetail(id);
    }

    private void addAiNotice(UUID id, long expectedVersion, String key, String explanation) {
        transaction.executeWithoutResult(status -> {
            SupportConversationRepository.Row locked = repository.lock(id).orElse(null);
            if (locked == null || !"AI_ACTIVE".equals(locked.state()) || locked.version() != expectedVersion) return;
            repository.addMessage(id, "SYSTEM", null, assistantName + " · aviso automático", explanation, key);
        });
    }

    private void escalateForHumanRequest(UUID id, long expectedVersion, String explanation) {
        transaction.executeWithoutResult(status -> {
            SupportConversationRepository.Row locked = repository.lock(id).orElse(null);
            if (locked == null || !"AI_ACTIVE".equals(locked.state()) || locked.version() != expectedVersion) return;
            if (repository.transition(id, locked.version(), "AI_ACTIVE", "WAITING_HUMAN", null, clock.instant()) == 1) {
                repository.event(id, "USER", locked.ownerUserId(), "STATE_CHANGED", "AI_ACTIVE", "WAITING_HUMAN", "Pedido explícito de atendimento humano.");
                repository.addMessage(id, "SYSTEM", null, "Porto Ajuda",
                        "Sua solicitação foi encaminhada para a equipe. As respostas são assíncronas; você pode enviar mais informações enquanto aguarda.",
                        "human-request-from-message:" + (locked.version() + 1));
            }
        });
    }

    private SupportConversationRepository.Row ownedLocked(UUID id, int userId) {
        SupportConversationRepository.Row row = repository.lock(id).orElseThrow(SupportAssistantService::notFound);
        if (row.ownerUserId() == null || row.ownerUserId() != userId) throw notFound();
        return row;
    }
    private void closeOwnedConversation(SupportConversationRepository.Row locked, int userId, String reason) {
        if (repository.transition(locked.id(), locked.version(), locked.state(), "CLOSED", null, clock.instant()) != 1)
            throw conflict("A conversa foi alterada; atualize e tente novamente.");
        repository.assignmentEvent(locked.id(), "USER", userId, "STATE_CHANGED", locked.state(), "CLOSED",
                reason, locked.assignedStaffUserId(), null);
    }
    private boolean assignedIsManageableBy(Integer actorId, Integer currentAssigneeId) {
        return actorId != null && actorId.equals(currentAssigneeId)
                || currentAssigneeId != null && !adminAuthorization.userHasPermission(currentAssigneeId, AdminPermission.SUPPORT_MANAGE);
    }
    private static boolean conversationalExchange(String message) {
        String normalized = SupportPublicContentSearch.normalize(message).replaceAll("[.!?,;\\s]+", " ").trim();
        return normalized.matches("(?:(?:oi|ola|olaa|bom dia|boa tarde|boa noite|tudo bem|tudo certo|e voce|e vc|obrigado|obrigada|valeu|entendi|certo|beleza|perfeito|legal|muito obrigado|muito obrigada)(?: (?:tudo bem|tudo certo|e voce|e vc))?){1,2}");
    }
    private static boolean explicitHumanRequest(String message) {
        String normalized = SupportPublicContentSearch.normalize(message);
        return normalized.matches("(?s).*(quero|preciso|gostaria|pode).{0,45}(falar|conversar|atendimento|ajuda).{0,35}(pessoa|humano|atendente|equipe).*")
                || normalized.matches("(?s).*(falar|conversar).{0,25}(com )?(uma )?(pessoa|humano|atendente).*")
                || normalized.matches("(?s).*(atendimento humano|atendente humano).*");
    }
    private static boolean outsideInitialScope(String message) {
        String normalized = SupportPublicContentSearch.normalize(message);
        return normalized.matches("(?s).*(\\b(meu|minha|meus|minhas) (cpf|perfil|conta|candidatura|candidaturas|senha|dados pessoais|documento|codigo|token)\\b|\\b(codigo de autenticacao|codigo de verificacao|mfa|status da minha candidatura|status das minhas candidaturas|recuperar senha|alterar senha|trocar senha|criar post|editar post|excluir post|publicar post|inscrever me|quero me inscrever|acesse minha conta|alterar meus dados)\\b).*");
    }
    private static boolean safeAccountNavigation(String message) {
        String normalized = SupportPublicContentSearch.normalize(message);
        if (normalized.matches("(?s).*(cpf|dados pessoais|candidatura|candidaturas|meus documentos|codigo de autenticacao|codigo de verificacao|mfa|token|pagamento|doacao|alterar senha|trocar senha|editar meus dados).*")) return false;
        boolean navigation = normalized.matches("(?s).*(como|onde|acessar|abrir|entrar|ir para|consultar|esqueci).*" );
        return navigation && (normalized.matches("(?s).*(perfil|configuracoes|configuracao).*" )
                || normalized.matches("(?s).*(recuperar senha|esqueci minha senha|recuperar acesso).*" ));
    }
    private List<SupportDtos.Message> historyBeforeLatestQuestion(UUID id, String body) {
        List<SupportDtos.Message> messages = repository.messages(id, 12);
        if (!messages.isEmpty()) {
            SupportDtos.Message last = messages.get(messages.size() - 1);
            if ("USER".equals(last.senderType()) && body.equals(last.body())) messages = messages.subList(0, messages.size() - 1);
        }
        List<SupportDtos.Message> bounded = messages.size() <= 10 ? messages : messages.subList(messages.size() - 10, messages.size());
        return bounded.stream().map(message -> "USER".equals(message.senderType())
                ? new SupportDtos.Message(message.id(), message.senderType(), message.senderLabel(),
                        minimizePrivateLocation(message.body()), message.createdAt())
                : message).toList();
    }
    private static String minimizePrivateLocation(String message) {
        if(message==null)return "";
        return GeminiSupportAssistantProvider.redact(message)
                .replaceAll("(?i)\\b(?:rua|avenida|av\\.?|alameda|travessa|rodovia|estrada|praca)\\s+[\\p{L}0-9'-]+(?:\\s+[\\p{L}0-9'-]+){0,4}(?:\\s*,\\s*\\d+[A-Za-z]?)?","[endereco omitido]")
                .replaceAll("(?<!\\d)-?\\d{1,3}\\.\\d{4,}\\s*,?\\s*-?\\d{1,3}\\.\\d{4,}(?!\\d)","[coordenadas omitidas]");
    }

    private SupportDtos.Conversation detail(SupportConversationRepository.Row row, boolean user) {
        boolean unread = user ? row.unreadUser() : row.unreadStaff();
        String assignedLabel = "HUMAN_ACTIVE".equals(row.state()) ? supportStaffName(row.assignedStaffUserId()) : null;
        return new SupportDtos.Conversation(row.id(), row.state(), assignedLabel,
                row.createdAt(), row.updatedAt(), row.closedAt(),
                row.closedAt()==null || row.contentPurgedAt()!=null ? null : row.closedAt().plus(Duration.ofDays(30)),
                row.version(), unread, repository.messages(row.id(), 200), repository.transitions(row.id()));
    }
    private String supportStaffName(Integer userId) {
        if (userId == null) return "Equipe Porto Ajuda";
        return assignments.findActiveSupportCandidates().stream()
                .filter(candidate -> candidate.userId().equals(userId))
                .filter(candidate -> com.portoajuda.aplicacao_osc.admin.AdminPermissionCatalog
                        .grants(candidate.profile(), AdminPermission.SUPPORT_MANAGE))
                .map(AdminAssignmentRepository.SupportStaffCandidate::displayName)
                .findFirst().map(name -> "Atendimento humano · " + name).orElse("Equipe Porto Ajuda");
    }
    private static int requireUser(Usuario user) {
        if (user == null || user.getId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre na sua conta para iniciar uma conversa.");
        return user.getId();
    }
    private void requireEnabled() { if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O atendimento por conversa está temporariamente indisponível."); }
    private static String bounded(String value, int max, boolean required) {
        if (value == null || value.isBlank() || value.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                required ? "Revise a mensagem; ela deve conter texto e respeitar o limite permitido." : "A solicitação é inválida.");
        return value.trim();
    }
    private static String idempotencyKey(String key) {
        try { return UUID.fromString(key).toString(); }
        catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador da mensagem inválido."); }
    }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada."); }
    private record SendSnapshot(SupportConversationRepository.Row row, boolean inserted) {}
    private record RetrySnapshot(SupportConversationRepository.Row row, String question, boolean claimed) {}
}
