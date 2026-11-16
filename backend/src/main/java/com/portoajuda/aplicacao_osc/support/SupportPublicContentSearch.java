package com.portoajuda.aplicacao_osc.support;

import com.portoajuda.aplicacao_osc.dto.response.OscListagemDTO;
import com.portoajuda.aplicacao_osc.dto.response.PostConteudoDTO;
import com.portoajuda.aplicacao_osc.dto.response.VagaConteudoDTO;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.service.OscService;
import com.portoajuda.aplicacao_osc.service.PostService;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Searches only the same bounded public service queries used by the NGO and content catalogs. */
@Service
public class SupportPublicContentSearch {
    private static final int SOURCE_LIMIT = 5;
    private static final int NGO_RECOMMENDATION_LIMIT = 3;
    private static final int SEARCH_PAGE_SIZE = 24;
    private static final Set<String> SEARCH_STOP_WORDS = Set.of("como", "onde", "qual", "quais", "uma", "umas", "uns", "dos", "das", "que", "para", "por", "pelo", "pela", "com", "sobre", "mostra", "apresenta", "faz", "fazer", "site", "porto", "ajuda", "ong", "ongs");
    private static final List<SupportDtos.SearchResult> SITE_GUIDANCE = List.of(
            new SupportDtos.SearchResult("guide:explore", "ORIENTAÇÃO", "Explorar o Porto Ajuda",
                    "Na página inicial, use os links Ver todas as ONGs, Ver todas as publicações e Ver todos os voluntariados para abrir os catálogos públicos.", "/catalogo?categoria=ongs"),
            new SupportDtos.SearchResult("guide:catalogo-ongs", "ORIENTAÇÃO", "Encontrar ONGs",
                    "O catálogo de ONGs públicas pode ser aberto pelo link Ver todas as ONGs ou diretamente em Explorar ONGs.", "/catalogo?categoria=ongs"),
            new SupportDtos.SearchResult("guide:catalogo-publicacoes", "ORIENTAÇÃO", "Encontrar publicações",
                    "O catálogo reúne publicações públicas de ONGs. Acesse Ver todas as publicações na página inicial.", "/catalogo?categoria=publicacoes"),
            new SupportDtos.SearchResult("guide:catalogo-voluntariado", "ORIENTAÇÃO", "Encontrar voluntariado",
                    "O catálogo mostra oportunidades públicas de voluntariado disponíveis. Acesse Ver todos os voluntariados na página inicial.", "/catalogo?categoria=voluntariado"),
            new SupportDtos.SearchResult("guide:profile", "ORIENTAÇÃO", "Abrir perfil e configurações",
                    "Para consultar as configurações da própria conta, entre normalmente e abra Perfil. A conversa não acessa dados privados nem solicita senha ou código de autenticação.", "/perfil"),
            new SupportDtos.SearchResult("guide:password-recovery", "ORIENTAÇÃO", "Recuperar acesso",
                    "Na tela de login, use Esqueci minha senha para iniciar a recuperação pelo endereço cadastrado. Não compartilhe senhas ou códigos nesta conversa.", "/login#recuperacao-senha"));
    private final OscService ngos;
    private final PostService posts;

    public SupportPublicContentSearch(OscService ngos, PostService posts) {
        this.ngos = ngos;
        this.posts = posts;
    }

    /** Facts and destinations maintained against implemented flows; the model may paraphrase, not extend them. */
    public List<SupportDtos.SearchResult> help(String rawTopic) {
        if (rawTopic == null) return List.of();
        return switch (rawTopic) {
            case "ACCESS" -> List.of(new SupportDtos.SearchResult("help:access", "ORIENTACAO", "Acesso à conta",
                    "O login fica na página de acesso. A recuperação usa o e-mail cadastrado; Sophie não pede nem recebe senha ou código de autenticação.", "/login"));
            case "NGO_REGISTRATION" -> List.of(new SupportDtos.SearchResult("help:ngo-registration", "ORIENTACAO", "Cadastro de ONG",
                    "O cadastro institucional é iniciado no formulário próprio. Novas ONGs passam por verificação de representação antes da visibilidade pública; revisão e aprovação são humanas.", "/pages/cadastro-osc.html"));
            case "REPRESENTATION_DOCUMENTS" -> List.of(new SupportDtos.SearchResult("help:representation-documents", "ORIENTACAO", "Documentos de representação",
                    "A verificação solicita documentos que conectem a pessoa representante à organização; para delegação, também é necessária autorização com escopo e validade. Certificado de CNPJ isolado não comprova representação.", "/pages/cadastro-osc.html"));
            case "PUBLICATIONS" -> List.of(new SupportDtos.SearchResult("help:publications", "ORIENTACAO", "Publicações de ONG",
                    "Publicações são conteúdo da ONG e devem ser administradas por integrante autorizado na própria área da organização. Sophie pode localizar conteúdo público, mas não publica nem edita.", "/catalogo?categoria=publicacoes"));
            case "VOLUNTEERING" -> List.of(new SupportDtos.SearchResult("help:volunteering", "ORIENTACAO", "Voluntariado",
                    "O catálogo apresenta oportunidades públicas disponíveis. A candidatura é feita pela página da oportunidade e exige login; Sophie não envia candidatura.", "/catalogo?categoria=voluntariado"));
            case "ACCESSIBILITY" -> List.of(new SupportDtos.SearchResult("help:accessibility", "ORIENTACAO", "Acessibilidade",
                    "O painel de acessibilidade está no cabeçalho/rodapé compartilhado. A assistência por voz é opcional e depende dos recursos do navegador; o chat continua disponível por texto.", ""));
            case "SUPPORT" -> List.of(new SupportDtos.SearchResult("help:support", "ORIENTACAO", "Atendimento humano",
                    "A conversa pode ser lida pela equipe autorizada. O atendimento humano é assíncrono; a fila não promete resposta imediata.", ""));
            case "DONATIONS" -> List.of(new SupportDtos.SearchResult("help:donations", "ORIENTACAO", "Doações",
                    "Sophie não inicia pagamentos nem confirma campanhas ou destinos financeiros. Consulte apenas informações públicas atualmente exibidas pela plataforma; não há ação financeira disponível pelo chat.", "/catalogo?categoria=doacoes"));
            case "PROFILE" -> List.of(new SupportDtos.SearchResult("help:profile", "ORIENTACAO", "Perfil e configurações",
                    "Depois de entrar na conta, o perfil reúne configurações e recursos próprios. O chat pode abrir a página, mas não lê nem altera dados privados.", "/perfil"));
            default -> List.of();
        };
    }

    public List<SupportDtos.SearchResult> openPage(String category, String cause, String topic) {
        String cat=category==null?"NONE":category;
        String route=switch(cat){case "NGO"->"ongs";case "PUBLICATION"->"publicacoes";case "VOLUNTEERING"->"voluntariado";default->null;};
        if(route!=null) {
            String href="/catalogo?categoria="+route;
            if(("NGO".equals(cat)||"PUBLICATION".equals(cat))&&cause!=null&&!"NONE".equals(cause)) {
                try { href+="&filtro="+CausaSocial.valueOf(cause).name(); } catch(IllegalArgumentException ignored) { /* unrecognized filter is omitted */ }
            }
            String label=switch(cat){case "NGO"->"ONGs";case "PUBLICATION"->"publicações";default->"oportunidades de voluntariado";};
            return List.of(new SupportDtos.SearchResult("page:"+cat+":"+cause,"NAVEGACAO","Abrir catálogo de "+label,
                    "A página abre o catálogo suportado; revise os filtros antes de continuar.",href));
        }
        return help(topic);
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.SearchResult> search(String rawQuery, String category) {
        return search(rawQuery, category, null, "");
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.SearchResult> search(String rawQuery, String category, CausaSocial explicitCause, String cityFilter) {
        String query = normalize(rawQuery);
        if (query.isBlank()) return List.of();
        if (query.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A busca deve ter até 120 caracteres.");
        String selected = category == null ? "ALL" : category.toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "NGO", "PUBLICATION", "VOLUNTEERING").contains(selected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria de busca inválida.");
        }
        List<SupportDtos.SearchResult> result = new ArrayList<>();
        if (selected.equals("ALL")) {
            SITE_GUIDANCE.stream().filter(guide -> matchesGuidance(query, guide)).limit(SOURCE_LIMIT)
                    .forEach(result::add);
            if (result.size() == SOURCE_LIMIT) return List.copyOf(result);
        }
        if (selected.equals("ALL") || selected.equals("NGO")) {
            appendOrganizations(query, result, explicitCause, cityFilter);
            if (result.size() == SOURCE_LIMIT) return List.copyOf(result);
        }
        if (selected.equals("ALL") || selected.equals("PUBLICATION")) {
            appendPublications(query, result, cityFilter);
            if (result.size() == SOURCE_LIMIT) return List.copyOf(result);
        }
        if (selected.equals("ALL") || selected.equals("VOLUNTEERING")) {
            appendVolunteering(query, result, cityFilter);
        }
        return List.copyOf(result);
    }

    /** Counts visible matches with a strict scan bound; incomplete scans are explicitly reported as a lower bound. */
    @Transactional(readOnly = true)
    public SupportDtos.SearchResult count(String rawQuery, String category, CausaSocial explicitCause, String cityFilter) {
        String query=normalize(rawQuery);
        String selected=category==null?"ALL":category.toUpperCase(Locale.ROOT);
        if(!Set.of("ALL","NGO","PUBLICATION","VOLUNTEERING").contains(selected))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Categoria de busca inválida.");
        int count=0; boolean truncated=false; int pages=0;
        if(selected.equals("ALL")||selected.equals("NGO")) {
            for(int pageNumber=0;pageNumber<40;pageNumber++) {
                var page=ngos.viewAll(PageRequest.of(pageNumber,SEARCH_PAGE_SIZE)); pages++;
                for(OscListagemDTO ngo:page.getContent()) if(matchesNgo(query,ngo,explicitCause==null?causeFromQuery(query):explicitCause)
                        &&matchesCity(cityFilter,ngo.cidade(),ngo.estado())) count++;
                if(!page.hasNext()) break;
                if(pageNumber==39)truncated=true;
            }
        }
        if(selected.equals("ALL")||selected.equals("PUBLICATION")) {
            for(int pageNumber=0;pageNumber<40;pageNumber++) {
                var page=posts.listarPublicacoesCatalogo(PageRequest.of(pageNumber,SEARCH_PAGE_SIZE));
                for(PostConteudoDTO post:page.getContent()) {
                    String searchable=normalize(String.join(" ",safe(post.titulo()),safe(post.conteudo()),safe(post.nomeOsc()),
                            safe(post.causaPrincipalOsc()==null?null:post.causaPrincipalOsc().name()),safe(post.cidadeOsc()),safe(post.estadoOsc())));
                    if(matchesSearchText(query,searchable)&&matchesCity(cityFilter,post.cidadeOsc(),post.estadoOsc()))count++;
                }
                if(!page.hasNext())break;
                if(pageNumber==39)truncated=true;
            }
        }
        if(selected.equals("ALL")||selected.equals("VOLUNTEERING")) {
            for(int pageNumber=0;pageNumber<40;pageNumber++) {
                var page=posts.listarVoluntariadosCatalogo(PageRequest.of(pageNumber,SEARCH_PAGE_SIZE));
                for(PostConteudoDTO post:page.getContent()) {
                    VagaConteudoDTO vacancy=post.vaga();
                    String searchable=normalize(String.join(" ",safe(post.titulo()),safe(post.conteudo()),safe(post.nomeOsc()),
                            safe(post.causaPrincipalOsc()==null?null:post.causaPrincipalOsc().name()),
                            vacancy==null?"":safe(vacancy.cidade()),vacancy==null?"":safe(vacancy.bairro())));
                    String city=vacancy==null?post.cidadeOsc():vacancy.cidade();
                    String region=vacancy==null?post.estadoOsc():vacancy.bairro();
                    if(matchesSearchText(query,searchable)&&matchesCity(cityFilter,city,region))count++;
                }
                if(!page.hasNext())break;
                if(pageNumber==39)truncated=true;
            }
        }
        String label=switch(selected){case "NGO"->count==1?"ONG":"ONGs";case "PUBLICATION"->count==1?"publicação":"publicações";case "VOLUNTEERING"->count==1?"oportunidade de voluntariado":"oportunidades de voluntariado";default->count==1?"item público":"itens públicos";};
        String amount=truncated?"pelo menos "+count:String.valueOf(count);
        String city=cityFilter==null||cityFilter.isBlank()?"":" em "+cityFilter;
        return new SupportDtos.SearchResult("count:"+selected,"CONTAGEM","Contagem de resultados públicos",
                "A consulta encontrou "+amount+" "+label+city+" dentro do recorte informado.",
                switch(selected){case "NGO"->"/catalogo?categoria=ongs";case "PUBLICATION"->"/catalogo?categoria=publicacoes";case "VOLUNTEERING"->"/catalogo?categoria=voluntariado";default->"/catalogo?categoria=ongs";});
    }

    /** Infers an existing public catalog from the user's wording; the provider never chooses its own data source. */
    public static String inferCategory(String rawQuery) {
        String query = normalize(rawQuery);
        if (hasAny(query, "voluntariado", "voluntario", "vaga", "oportunidade", "volunteer")) return "VOLUNTEERING";
        if (hasAny(query, "publicacao", "publicacoes", "post", "posts", "noticia", "campaign", "campanha")) return "PUBLICATION";
        if (causeFromQuery(query) != null || hasAny(query, "ong", "ongs", "organizacao", "organization", "instituicao")) return "NGO";
        return "ALL";
    }

    /** Uses only the current user turn as a conservative fallback when the structured plan omits a known cause. */
    public static CausaSocial inferCurrentTurnCause(String rawQuery) {
        String query=normalize(rawQuery);
        if(hasAny(query,"esquece","mudei de ideia","em vez de","nao quero mais","deixa a causa","troque a causa")) return null;
        return causeFromQuery(query);
    }

    static CatalogSearchDraft catalogSearchDraft(String rawQuery, String category) {
        CausaSocial cause = causeFromQuery(normalize(rawQuery));
        if (cause == null || !("NGO".equals(category) || "PUBLICATION".equals(category))) return null;
        String catalogCategory = "NGO".equals(category) ? "ongs" : "publicacoes";
        return new CatalogSearchDraft("/catalogo?categoria=" + catalogCategory + "&filtro=" + cause.name(),
                causeLabel(cause));
    }

    record CatalogSearchDraft(String href, String filterLabel) {}

    private void appendOrganizations(String query, List<SupportDtos.SearchResult> result, CausaSocial explicitCause, String cityFilter) {
        CausaSocial requestedCause = explicitCause == null ? causeFromQuery(query) : explicitCause;
        int pageNumber = 0;
        int matches = 0;
        while (result.size() < SOURCE_LIMIT && matches < NGO_RECOMMENDATION_LIMIT) {
            var page = ngos.viewAll(PageRequest.of(pageNumber, SEARCH_PAGE_SIZE));
            for (OscListagemDTO ngo : page.getContent()) {
                if (!matchesNgo(query, ngo, requestedCause) || !matchesCity(cityFilter, ngo.cidade(), ngo.estado())) continue;
                String causeText = publicCauseLabels(ngo);
                String location = location(ngo.cidade(), ngo.estado());
                String summary = joinFacts("Causas registradas no perfil público: " + (causeText.isBlank() ? "não informadas" : causeText),
                        location.isBlank() ? "" : "Local público: " + location,
                        ngo.descricaoCurta() == null || ngo.descricaoCurta().isBlank() ? ""
                                : "Descrição pública: " + excerpt(ngo.descricaoCurta()));
                result.add(new SupportDtos.SearchResult("ngo:" + ngo.id(), "ONG", safe(ngo.nome()), summary,
                        "/ong-profile?id=" + ngo.id()));
                matches++;
                if (result.size() == SOURCE_LIMIT || matches == NGO_RECOMMENDATION_LIMIT) break;
            }
            if (!page.hasNext()) break;
            pageNumber++;
        }
    }

    private void appendPublications(String query, List<SupportDtos.SearchResult> result, String cityFilter) {
        int pageNumber = 0;
        while (result.size() < SOURCE_LIMIT) {
            var page = posts.listarPublicacoesCatalogo(PageRequest.of(pageNumber, SEARCH_PAGE_SIZE));
            for (PostConteudoDTO post : page.getContent()) {
                String searchable = String.join(" ", safe(post.titulo()), safe(post.conteudo()), safe(post.nomeOsc()),
                        safe(post.causaPrincipalOsc() == null ? null : post.causaPrincipalOsc().name()),
                        safe(post.cidadeOsc()), safe(post.estadoOsc()));
                if (!matchesSearchText(query, normalize(searchable)) || !matchesCity(cityFilter, post.cidadeOsc(), post.estadoOsc())) continue;
                String summary = joinFacts("Publicação de " + safe(post.nomeOsc()),
                        publicCauseLabel(post.causaPrincipalOsc()), location(post.cidadeOsc(), post.estadoOsc()),
                        "Conteúdo público: " + excerpt(post.conteudo()));
                result.add(new SupportDtos.SearchResult("publication:" + post.id(), "PUBLICAÇÃO", safe(post.titulo()),
                        summary, "/ong-profile?id=" + post.oscId() + "#publicacoes"));
                if (result.size() == SOURCE_LIMIT) return;
            }
            if (!page.hasNext()) break;
            pageNumber++;
        }
    }

    private void appendVolunteering(String query, List<SupportDtos.SearchResult> result, String cityFilter) {
        int pageNumber = 0;
        while (result.size() < SOURCE_LIMIT) {
            var page = posts.listarVoluntariadosCatalogo(PageRequest.of(pageNumber, SEARCH_PAGE_SIZE));
            for (PostConteudoDTO post : page.getContent()) {
                VagaConteudoDTO vacancy = post.vaga();
                String vacancyFacts = vacancy == null ? "" : String.join(" ", safe(vacancy.cidade()), safe(vacancy.bairro()),
                        safe(vacancy.modalidadeVoluntariado() == null ? null : vacancy.modalidadeVoluntariado().name()));
                String searchable = String.join(" ", safe(post.titulo()), safe(post.conteudo()), safe(post.nomeOsc()),
                        safe(post.causaPrincipalOsc() == null ? null : post.causaPrincipalOsc().name()), vacancyFacts);
                String activityCity = vacancy == null ? post.cidadeOsc() : vacancy.cidade();
                if (!matchesSearchText(query, normalize(searchable)) || !matchesCity(cityFilter, activityCity, vacancy == null ? post.estadoOsc() : vacancy.bairro())) continue;
                String activityLocation = vacancy == null ? location(post.cidadeOsc(), post.estadoOsc())
                        : location(vacancy.cidade(), vacancy.bairro());
                String summary = joinFacts("Oportunidade de " + safe(post.nomeOsc()),
                        vacancy == null || vacancy.modalidadeVoluntariado() == null ? ""
                                : "Modalidade: " + modalityLabel(vacancy.modalidadeVoluntariado().name()),
                        activityLocation.isBlank() ? "" : "Local da atividade: " + activityLocation,
                        vacancy == null || vacancy.quantidadeVagas() == null ? ""
                                : "Vagas publicadas: " + vacancy.quantidadeVagas(),
                        "Descrição pública: " + excerpt(post.conteudo()));
                result.add(new SupportDtos.SearchResult("volunteering:" + post.id(), "VOLUNTARIADO", safe(post.titulo()),
                        summary, "/ong-profile?id=" + post.oscId() + "#voluntariado"));
                if (result.size() == SOURCE_LIMIT) return;
            }
            if (!page.hasNext()) break;
            pageNumber++;
        }
    }

    private static boolean matchesNgo(String query, OscListagemDTO ngo, CausaSocial requestedCause) {
        if (requestedCause != null) {
            return causes(ngo).contains(requestedCause);
        }
        String searchable = String.join(" ", safe(ngo.nome()), causes(ngo).stream().map(Enum::name).reduce("", (a, b) -> a + " " + b),
                safe(ngo.cidade()), safe(ngo.estado()), safe(ngo.descricaoCurta()), safe(ngo.descricao()));
        return matchesSearchText(query, normalize(searchable));
    }

    private static boolean matchesCity(String cityFilter, String city, String region) {
        if (cityFilter == null || cityFilter.isBlank()) return true;
        String wanted = normalize(cityFilter);
        return normalize(safe(city)).contains(wanted) || normalize(safe(region)).contains(wanted);
    }

    private static Set<CausaSocial> causes(OscListagemDTO ngo) {
        LinkedHashSet<CausaSocial> result = new LinkedHashSet<>();
        if (ngo.causaPrincipal() != null) result.add(ngo.causaPrincipal());
        if (ngo.causasSecundarias() != null) result.addAll(ngo.causasSecundarias());
        if (ngo.causas() != null) result.addAll(ngo.causas());
        return result;
    }

    private static CausaSocial causeFromQuery(String query) {
        if (hasAny(query, "animal", "animais", "pet", "pets", "cachorro", "cachorros", "cao", "caes", "gato", "gatos", "fauna", "bicho", "bichos")) return CausaSocial.ANIMAIS;
        if (hasAny(query, "meio ambiente", "natureza", "clima", "sustentabilidade", "ecologia")) return CausaSocial.MEIO_AMBIENTE;
        if (hasAny(query, "educacao", "escola", "ensino", "aprendizagem")) return CausaSocial.EDUCACAO;
        if (hasAny(query, "saude", "hospital", "tratamento", "prevencao")) return CausaSocial.SAUDE;
        if (hasAny(query, "inclusao", "acessibilidade", "deficiencia", "inclusiva")) return CausaSocial.INCLUSAO_SOCIAL;
        if (hasAny(query, "esporte", "futebol", "atividade esportiva")) return CausaSocial.ESPORTE;
        if (hasAny(query, "cultura", "arte", "musica", "teatro")) return CausaSocial.CULTURA;
        if (hasAny(query, "pobreza", "fome", "renda", "seguranca alimentar")) return CausaSocial.COMBATE_POBREZA;
        return null;
    }

    private static String publicCauseLabels(OscListagemDTO ngo) {
        return causes(ngo).stream().map(SupportPublicContentSearch::causeLabel).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String publicCauseLabel(CausaSocial cause) { return cause == null ? "" : causeLabel(cause); }

    private static String causeLabel(CausaSocial cause) {
        return switch (cause) {
            case ANIMAIS -> "proteção animal";
            case MEIO_AMBIENTE -> "meio ambiente";
            case COMBATE_POBREZA -> "combate à pobreza";
            case INCLUSAO_SOCIAL -> "inclusão social";
            case EDUCACAO -> "educação";
            case SAUDE -> "saúde";
            case ESPORTE -> "esporte";
            case CULTURA -> "cultura";
        };
    }

    private static String modalityLabel(String modality) {
        return switch (modality) { case "PRESENCIAL" -> "presencial"; case "REMOTO" -> "remota"; case "HIBRIDO" -> "híbrida"; default -> modality; };
    }

    private static String location(String city, String region) {
        return joinFacts(city, region);
    }

    private static String joinFacts(String... facts) {
        return java.util.Arrays.stream(facts).filter(value -> value != null && !value.isBlank())
                .map(String::strip).reduce((a, b) -> a + ". " + b).orElse("");
    }

    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }
    private static boolean matchesGuidance(String query, SupportDtos.SearchResult guide) {
        String text = normalize(guide.title() + " " + guide.summary() + " " + guide.href());
        if ("guide:explore".equals(guide.id()) && text.contains(query)) return true;
        return switch (guide.id()) {
            case "guide:catalogo-ongs" -> hasAny(query, "ong", "organiza") && hasAny(query, "encontr", "encontro", "achar", "localiz", "buscar", "busc", "ver", "acess", "onde");
            case "guide:catalogo-publicacoes" -> hasAny(query, "publica", "post", "conteudo") && hasAny(query, "encontr", "encontro", "achar", "localiz", "buscar", "busc", "ver", "acess", "onde");
            case "guide:catalogo-voluntariado" -> hasAny(query, "volunt", "vaga", "oportunidade") && hasAny(query, "encontr", "encontro", "achar", "localiz", "buscar", "busc", "ver", "acess", "onde");
            case "guide:profile" -> hasAny(query, "perfil", "configuracao", "configuracoes") && hasAny(query, "acess", "abrir", "onde", "entrar", "ir para", "consultar", "ver");
            case "guide:password-recovery" -> hasAny(query, "senha", "acesso", "recuperar") && hasAny(query, "recuperar", "esqueci", "login", "senha");
            case "guide:explore" -> hasAny(query, "site", "pagina", "naveg", "explor", "começar", "comecar", "usar");
            default -> false;
        };
    }
    private static boolean hasAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }
    private static boolean matchesSearchText(String query, String searchableText) {
        if (searchableText.contains(query)) return true;
        return java.util.Arrays.stream(query.split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() >= 3 && !SEARCH_STOP_WORDS.contains(token))
                .anyMatch(searchableText::contains);
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String excerpt(String value) {
        String plain = (value == null ? "" : value).replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return plain.length() <= 320 ? plain : plain.substring(0, 317) + "…";
    }
}
