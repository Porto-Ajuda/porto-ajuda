package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.service.OscService;
import com.portoajuda.aplicacao_osc.service.PostService;
import com.portoajuda.aplicacao_osc.dto.response.OscListagemDTO;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

class SupportPublicContentSearchTest {
    @Test void siteNavigationGuidanceUsesImplementedPublicCatalogRoutes() {
        OscService ngos = mock(OscService.class);
        PostService posts = mock(PostService.class);
        when(ngos.viewAll(any(Pageable.class))).thenReturn(Page.empty());
        when(posts.listarPublicacoesCatalogo(any(Pageable.class))).thenReturn(Page.empty());
        when(posts.listarVoluntariadosCatalogo(any(Pageable.class))).thenReturn(Page.empty());

        List<SupportDtos.SearchResult> results = new SupportPublicContentSearch(ngos, posts)
                .search("Como encontro ONGs?", "ALL");

        var guide = results.stream().filter(item -> item.id().equals("guide:catalogo-ongs")).findFirst().orElseThrow();
        assertEquals("/catalogo?categoria=ongs", guide.href());
        assertTrue(guide.summary().contains("Ver todas as ONGs"));
    }

    @Test void profileAndPasswordRecoveryGuidesUseOnlyNormalAuthenticatedRoutes() {
        OscService ngos = mock(OscService.class);
        PostService posts = mock(PostService.class);
        when(ngos.viewAll(any(Pageable.class))).thenReturn(Page.empty());
        when(posts.listarPublicacoesCatalogo(any(Pageable.class))).thenReturn(Page.empty());
        when(posts.listarVoluntariadosCatalogo(any(Pageable.class))).thenReturn(Page.empty());
        var search = new SupportPublicContentSearch(ngos, posts);

        var profile = search.search("Como acesso meu perfil e configurações?", "ALL").stream()
                .filter(item -> item.id().equals("guide:profile")).findFirst().orElseThrow();
        var recovery = search.search("Como recuperar senha na tela de login?", "ALL").stream()
                .filter(item -> item.id().equals("guide:password-recovery")).findFirst().orElseThrow();

        assertEquals("/perfil", profile.href());
        assertEquals("/login#recuperacao-senha", recovery.href());
        assertTrue(profile.summary().contains("não acessa dados privados"));
        assertTrue(recovery.summary().contains("Não compartilhe senhas"));
    }

    @Test void naturalLanguageQuestionFindsOnlyTheMatchingPublicNgoSource() {
        OscService ngos = mock(OscService.class);
        PostService posts = mock(PostService.class);
        var publicNgo = new OscListagemDTO(782001, "ONG Arco Fictícia", null, CausaSocial.CULTURA,
                Set.of(), Set.of(CausaSocial.CULTURA), Set.of(), StatusOsc.ATIVA,
                "Mantém uma biblioteca comunitária fictícia.", "Conteúdo público de teste.", null, null,
                null, "Praia Grande", "SP", "Brasil", 0, 0);
        when(ngos.viewAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(publicNgo)));
        when(posts.listarPublicacoesCatalogo(any(Pageable.class))).thenReturn(Page.empty());
        when(posts.listarVoluntariadosCatalogo(any(Pageable.class))).thenReturn(Page.empty());

        var result = new SupportPublicContentSearch(ngos, posts)
                .search("O que a ONG Arco Fictícia faz?", "ALL");

        assertEquals(1, result.size());
        assertEquals("ONG Arco Fictícia", result.getFirst().title());
        assertEquals("/ong-profile?id=782001", result.getFirst().href());
    }

    @Test void animalCauseSearchIncludesSecondaryCausesAndGroundsPublicRecommendations() {
        OscService ngos = mock(OscService.class);
        PostService posts = mock(PostService.class);
        var animalNgo = new OscListagemDTO(782101, "Abrigo Fictício Patas", null, CausaSocial.CULTURA,
                Set.of(CausaSocial.ANIMAIS), Set.of(CausaSocial.CULTURA, CausaSocial.ANIMAIS), Set.of(), StatusOsc.ATIVA,
                "Acolhe cães e gatos resgatados.", "Texto público sintético.", null, null,
                "Centro", "Praia Grande", "SP", "Brasil", 0, 0);
        when(ngos.viewAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(animalNgo)));
        when(posts.listarPublicacoesCatalogo(any(Pageable.class))).thenReturn(Page.empty());
        when(posts.listarVoluntariadosCatalogo(any(Pageable.class))).thenReturn(Page.empty());

        String query = "Quais ONGs ajudam animais?";
        var result = new SupportPublicContentSearch(ngos, posts).search(query,
                SupportPublicContentSearch.inferCategory(query));

        assertEquals("NGO", SupportPublicContentSearch.inferCategory(query));
        assertEquals(1, result.size());
        assertEquals("ONG", result.getFirst().category());
        assertEquals("Abrigo Fictício Patas", result.getFirst().title());
        assertTrue(result.getFirst().summary().contains("proteção animal"));
        assertTrue(result.getFirst().summary().contains("Local público: Praia Grande. SP"));
        assertTrue(result.getFirst().summary().contains("Acolhe cães e gatos"));
        assertEquals("/ong-profile?id=782101", result.getFirst().href());
    }

    @Test void animalSearchPagesThroughTheAuthorizedPublicListingUntilThreeMatchesOrEnd() {
        OscService ngos = mock(OscService.class);
        PostService posts = mock(PostService.class);
        var animalNgo = new OscListagemDTO(782102, "Projeto Fictício Fauna", null, CausaSocial.CULTURA,
                Set.of(CausaSocial.ANIMAIS), Set.of(CausaSocial.CULTURA, CausaSocial.ANIMAIS), Set.of(), StatusOsc.ATIVA,
                "Reabilita fauna silvestre.", "Texto público sintético.", null, null,
                "Centro", "Santos", "SP", "Brasil", 0, 0);
        when(ngos.viewAll(any(Pageable.class))).thenAnswer(call -> {
            Pageable pageable = call.getArgument(0);
            return pageable.getPageNumber() == 0
                    ? new PageImpl<>(List.of(), PageRequest.of(0, 24), 25)
                    : new PageImpl<>(List.of(animalNgo), PageRequest.of(1, 24), 25);
        });
        when(posts.listarPublicacoesCatalogo(any(Pageable.class))).thenReturn(Page.empty());
        when(posts.listarVoluntariadosCatalogo(any(Pageable.class))).thenReturn(Page.empty());

        var results = new SupportPublicContentSearch(ngos, posts).search("ONGs de fauna", "NGO");

        assertEquals(1, results.size());
        assertEquals("Projeto Fictício Fauna", results.getFirst().title());
        org.mockito.Mockito.verify(ngos, org.mockito.Mockito.times(2)).viewAll(any(Pageable.class));
    }

    @Test void countUsesValidatedCategoryCauseAndCityAndReturnsServerCount() {
        OscService ngos=mock(OscService.class); PostService posts=mock(PostService.class);
        var praia=new OscListagemDTO(782201,"Abrigo Sintético A",null,CausaSocial.CULTURA,
                Set.of(CausaSocial.ANIMAIS),Set.of(CausaSocial.ANIMAIS),Set.of(),StatusOsc.ATIVA,
                "Descrição",null,null,null,"Centro","Praia Grande","SP","Brasil",0,0);
        var outra=new OscListagemDTO(782202,"Abrigo Sintético B",null,CausaSocial.CULTURA,
                Set.of(CausaSocial.ANIMAIS),Set.of(CausaSocial.ANIMAIS),Set.of(),StatusOsc.ATIVA,
                "Descrição",null,null,null,"Centro","Santos","SP","Brasil",0,0);
        when(ngos.viewAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(praia,outra)));
        when(posts.listarPublicacoesCatalogo(any(Pageable.class))).thenReturn(Page.empty());
        when(posts.listarVoluntariadosCatalogo(any(Pageable.class))).thenReturn(Page.empty());
        var count=new SupportPublicContentSearch(ngos,posts).count("proteção animal","NGO",CausaSocial.ANIMAIS,"Praia Grande");
        assertEquals("CONTAGEM",count.category());
        assertTrue(count.summary().contains("1 ONG"));
        assertTrue(count.summary().contains("Praia Grande"));
    }

    @Test void supportedPageDraftUsesServerAllowlistAndCannotBecomeAnArbitraryUrl() {
        var search=new SupportPublicContentSearch(mock(OscService.class),mock(PostService.class));
        var page=search.openPage("NGO","ANIMAIS","NONE").getFirst();
        assertEquals("/catalogo?categoria=ongs&filtro=ANIMAIS",page.href());
        assertTrue(search.openPage("https://evil.invalid","NONE","NONE").isEmpty());
        var donationHelp=search.help("DONATIONS").getFirst();
        assertTrue(donationHelp.summary().contains("não inicia pagamentos"));
    }

    @Test void currentTurnCauseFallbackDoesNotCarryForwardAnExplicitCorrection() {
        assertEquals(CausaSocial.ANIMAIS,SupportPublicContentSearch.inferCurrentTurnCause("Quais ONGs ajudam animais?"));
        assertNull(SupportPublicContentSearch.inferCurrentTurnCause("Esquece os animais; agora quero voluntariado remoto."));
    }
}
