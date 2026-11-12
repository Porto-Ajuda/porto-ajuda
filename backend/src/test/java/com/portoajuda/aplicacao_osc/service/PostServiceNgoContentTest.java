package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminOperationAuditService;
import com.portoajuda.aplicacao_osc.dto.request.RequestCreatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestPostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestUpdatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestVagaDTO;
import com.portoajuda.aplicacao_osc.dto.response.PostConteudoDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.ModalidadeVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PostServiceNgoContentTest {

    @Mock private OscRepository oscRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PostRepository postRepository;
    @Mock private VagaRepository vagaRepository;
    @Mock private AdminAuthorizationService adminAuthorization;
    @Mock private AdminOperationAuditService operationAudit;
    @Mock private OscMembrosRepository membrosRepository;
    @Mock private CandidaturasVoluntariadoRepository candidaturasRepository;
    @Mock private OscService oscService;
    @Mock private SupabaseStorageService storageService;
    @Mock private RoleRepository roleRepository;
    @InjectMocks private PostService service;

    private Osc osc;
    private Usuario actor;

    @BeforeEach
    void setUp() {
        osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", 101);
        osc.setStatus(StatusOsc.INATIVA);

        actor = new Usuario();
        ReflectionTestUtils.setField(actor, "id", 7);
        actor.setAtivo(true);

        lenient().when(oscRepository.findById(101)).thenReturn(Optional.of(osc));
        lenient().when(usuarioRepository.findById(7)).thenReturn(Optional.of(actor));
        lenient().when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            if (post.getId() == null) ReflectionTestUtils.setField(post, "id", 900);
            return post;
        });
        lenient().when(vagaRepository.save(any(Vaga.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void allFourMembershipRolesCanCreateContentForTheSelectedOrganization() {
        for (CargoOsc cargo : CargoOsc.values()) {
            when(oscService.obterCargoUsuario(osc, actor)).thenReturn(cargo);
            PostConteudoDTO created = service.createInOsc(101, createPost("DOACAO", null), actor);
            assertEquals(101, created.oscId());
            assertEquals(TipoPost.DOACAO, created.tipo());
            assertTrue(created.podeEditar());
        }
        verify(oscService, org.mockito.Mockito.times(CargoOsc.values().length))
                .obterCargoUsuario(osc, actor);
        verify(postRepository, org.mockito.Mockito.times(CargoOsc.values().length)).save(any(Post.class));
    }

    @Test
    void opportunityIsStoredAsOnePostAndOneLinkedOpenVacancy() {
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.MEMBRO);
        RequestVagaDTO vagaRequest = vagaRequest("ABERTA");

        PostConteudoDTO created = service.createInOsc(101, createPost("VOLUNTARIADO", vagaRequest), actor);

        assertEquals(TipoPost.VOLUNTARIADO, created.tipo());
        assertNotNull(created.vaga());
        assertEquals(StatusVaga.ABERTA, created.vaga().status());
        org.mockito.ArgumentCaptor<Post> postCaptor = org.mockito.ArgumentCaptor.forClass(Post.class);
        org.mockito.ArgumentCaptor<Vaga> vagaCaptor = org.mockito.ArgumentCaptor.forClass(Vaga.class);
        verify(postRepository).save(postCaptor.capture());
        verify(vagaRepository).save(vagaCaptor.capture());
        assertEquals(postCaptor.getValue(), vagaCaptor.getValue().getPost());
        assertEquals(osc, postCaptor.getValue().getOsc());
        assertEquals(actor, postCaptor.getValue().getUsuario());
    }

    @Test
    void anUnrelatedOrganizationMembershipCannotCreateContent() {
        doThrow(new AccessDeniedException("membership required"))
                .when(oscService).obterCargoUsuario(osc, actor);

        assertThrows(AccessDeniedException.class,
                () -> service.createInOsc(101, createPost("DOACAO", null), actor));
        verify(postRepository, never()).save(any(Post.class));
        verify(vagaRepository, never()).save(any(Vaga.class));
        verify(storageService, never()).enviarImagemConteudoOsc(any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void selectedOrganizationIsUsedWhenTheActorHasMultipleMemberships() {
        Osc other = new Osc();
        ReflectionTestUtils.setField(other, "id", 202);
        other.setStatus(StatusOsc.ATIVA);
        when(oscRepository.findById(202)).thenReturn(Optional.of(other));
        when(oscService.obterCargoUsuario(other, actor)).thenReturn(CargoOsc.EDITOR);

        PostConteudoDTO created = service.createInOsc(202, createPost("DOACAO", null), actor);

        assertEquals(202, created.oscId());
        org.mockito.ArgumentCaptor<Post> captor = org.mockito.ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertEquals(other, captor.getValue().getOsc());
        verify(oscService).obterCargoUsuario(other, actor);
    }

    @Test
    void contentFromAnotherOrganizationCannotBeEditedThroughTheSelectedOrganizationRoute() {
        Post post = post(TipoPost.DOACAO, 55);
        Osc other = new Osc();
        ReflectionTestUtils.setField(other, "id", 202);
        post.setOsc(other);
        when(postRepository.findById(900)).thenReturn(Optional.of(post));
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.CRIADOR);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.atualizarConteudoDaOsc(101, updateRequest(900, "tamper"), actor,
                        new MockMultipartFile("imagem", "cross-ngo.png", "image/png", pngValido())));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(postRepository, never()).save(any(Post.class));
        verify(storageService, never()).enviarImagemConteudoOsc(any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void ngoMembersCannotCreatePlatformNews() {
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.CRIADOR);
        RequestCreatePostDTO news = new RequestCreatePostDTO(
                new RequestPostDTO("NOTICIA", true, "Noticia", null, "Texto"), null);

        assertThrows(AccessDeniedException.class, () -> service.createInOsc(101, news, actor));
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void ngoMemberCannotPersistAClientSuppliedImageUrl() {
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.MEMBRO);
        RequestCreatePostDTO request = new RequestCreatePostDTO(
                new RequestPostDTO("DOACAO", true, "Post", "https://attacker.example/image.png", "Texto"), null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createInOsc(101, request, actor));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(storageService, never()).enviarImagemConteudoOsc(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), any());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void authorizedMemberPersistsOnlyTheStorageUrlReturnedByTheServer() {
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.MEMBRO);
        when(storageService.enviarImagemConteudoOsc(eq(101), eq(900), eq(false),
                any(byte[].class), eq("image/png")))
                .thenReturn(new SupabaseStorageService.ArquivoEnviado(
                        "https://storage.example.invalid/oscs/101/posts/900/abc.png",
                        "oscs/101/posts/900/00000000-0000-0000-0000-000000000001.png"));
        MockMultipartFile image = new MockMultipartFile("imagem", "original.png", "image/png", pngValido());
        TransactionSynchronizationManager.initSynchronization();
        try {
            PostConteudoDTO result = service.createInOsc(101, createPost("DOACAO", null), actor, image);

            assertEquals("https://storage.example.invalid/oscs/101/posts/900/abc.png", result.imagemUrl());
            org.mockito.ArgumentCaptor<Post> saved = org.mockito.ArgumentCaptor.forClass(Post.class);
            verify(postRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
            assertEquals("https://storage.example.invalid/oscs/101/posts/900/abc.png",
                    saved.getAllValues().getLast().getImagemUrl());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aMemberCannotModifyContentFromAHigherCurrentRole() {
        Post post = post(TipoPost.DOACAO, 55);
        RequestUpdatePostDTO update = updateRequest(post.getId(), "updated");
        when(postRepository.findById(900)).thenReturn(Optional.of(post));
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.EDITOR);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(101, 55)).thenReturn(Optional.of(member(CargoOsc.ADMINISTRADOR)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.atualizarConteudoDaOsc(101, update, actor,
                        new MockMultipartFile("imagem", "higher-role.png", "image/png", pngValido())));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(postRepository, never()).save(any(Post.class));
        verify(storageService, never()).enviarImagemConteudoOsc(any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void aHigherRoleCanModifyContentFromALowerCurrentRole() {
        Post post = post(TipoPost.DOACAO, 55);
        when(postRepository.findById(900)).thenReturn(Optional.of(post));
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.ADMINISTRADOR);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(101, 55)).thenReturn(Optional.of(member(CargoOsc.EDITOR)));

        PostConteudoDTO updated = service.atualizarConteudoDaOsc(101, updateRequest(900, "updated"), actor);

        assertTrue(updated.podeEditar());
        assertEquals("updated", post.getTitulo());
        verify(postRepository).save(post);
    }

    @Test
    void editingWithoutReplacementPreservesTheExistingImageAndRejectsNewUrls() {
        Post post = post(TipoPost.DOACAO, 55);
        post.setImagemUrl("https://legacy.example.invalid/existing.jpg");
        when(postRepository.findById(900)).thenReturn(Optional.of(post));
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.ADMINISTRADOR);
        when(membrosRepository.findByOsc_IdAndUsuario_Id(101, 55))
                .thenReturn(Optional.of(member(CargoOsc.EDITOR)));

        service.atualizarConteudoDaOsc(101, updateRequest(900, "updated"), actor);
        assertEquals("https://legacy.example.invalid/existing.jpg", post.getImagemUrl());

        RequestUpdatePostDTO withExternalUrl = new RequestUpdatePostDTO(900,
                new RequestPostDTO("DOACAO", true, "another edit",
                        "https://attacker.example/replace.png", "Conteudo"), null);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.atualizarConteudoDaOsc(101, withExternalUrl, actor));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void volunteerImageUsesTheOpportunityNamespaceAndServerReturnedUrl() {
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.MEMBRO);
        when(storageService.enviarImagemConteudoOsc(eq(101), eq(900), eq(true),
                any(byte[].class), eq("image/png")))
                .thenReturn(new SupabaseStorageService.ArquivoEnviado(
                        "https://storage.example.invalid/oscs/101/voluntariado/900/abc.png",
                        "oscs/101/voluntariado/900/00000000-0000-0000-0000-000000000002.png"));
        MockMultipartFile image = new MockMultipartFile("imagem", "atividade.png", "image/png", pngValido());
        TransactionSynchronizationManager.initSynchronization();
        try {
            PostConteudoDTO result = service.createInOsc(101,
                    createPost("VOLUNTARIADO", vagaRequest("ABERTA")), actor, image);

            assertEquals("https://storage.example.invalid/oscs/101/voluntariado/900/abc.png", result.imagemUrl());
            verify(storageService).enviarImagemConteudoOsc(101, 900, true, image.getBytes(), "image/png");
        } catch (java.io.IOException erro) {
            throw new IllegalStateException(erro);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void authorizedEditReplacesImageOnlyWithTheUrlReturnedByStorage() throws Exception {
        Post post = post(TipoPost.DOACAO, 55);
        post.setUsuario(actor);
        post.setImagemUrl("https://legacy.example.invalid/old.png");
        when(postRepository.findById(900)).thenReturn(Optional.of(post));
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.MEMBRO);
        when(storageService.enviarImagemConteudoOsc(eq(101), eq(900), eq(false),
                any(byte[].class), eq("image/png")))
                .thenReturn(new SupabaseStorageService.ArquivoEnviado(
                        "https://storage.example.invalid/oscs/101/posts/900/new.png",
                        "oscs/101/posts/900/00000000-0000-0000-0000-000000000004.png"));
        MockMultipartFile image = new MockMultipartFile("imagem", "new.png", "image/png", pngValido());
        TransactionSynchronizationManager.initSynchronization();
        try {
            PostConteudoDTO updated = service.atualizarConteudoDaOsc(
                    101, updateRequest(900, "updated"), actor, image);

            assertEquals("https://storage.example.invalid/oscs/101/posts/900/new.png", updated.imagemUrl());
            assertEquals("https://storage.example.invalid/oscs/101/posts/900/new.png", post.getImagemUrl());
            verify(storageService).enviarImagemConteudoOsc(101, 900, false, image.getBytes(), "image/png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void membersCanManageAnInactiveButUnarchivedOrganizationWhilePublicReadsAreHidden() {
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.MEMBRO);
        when(postRepository.findByOsc_IdOrderByDataCriacaoDesc(101)).thenReturn(List.of());

        assertTrue(service.listarConteudoDaOsc(101, actor).isEmpty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.listarConteudoPublicoDaOsc(101));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(postRepository, never()).findPublicosPorOsc(101, StatusOsc.ATIVA);
    }

    @Test
    void publicReadsIncludeActivePostsAndOnlyOpenUnexpiredOpportunities() {
        osc.setStatus(StatusOsc.ATIVA);
        Post donation = post(TipoPost.DOACAO, 7);
        ReflectionTestUtils.setField(donation, "id", 901);
        donation.setAtivo(true);
        Post open = post(TipoPost.VOLUNTARIADO, 7);
        ReflectionTestUtils.setField(open, "id", 902);
        open.setAtivo(true);
        Vaga openVacancy = vacancy(StatusVaga.ABERTA, LocalDate.now().plusDays(4));
        open.setVagaVoluntariado(openVacancy);
        Post closed = post(TipoPost.VOLUNTARIADO, 7);
        ReflectionTestUtils.setField(closed, "id", 903);
        closed.setAtivo(true);
        closed.setVagaVoluntariado(vacancy(StatusVaga.ENCERRADA, LocalDate.now().plusDays(4)));
        Post expired = post(TipoPost.VOLUNTARIADO, 7);
        ReflectionTestUtils.setField(expired, "id", 904);
        expired.setAtivo(true);
        expired.setVagaVoluntariado(vacancy(StatusVaga.ABERTA, LocalDate.now().minusDays(1)));
        when(postRepository.findPublicosPorOsc(101, StatusOsc.ATIVA))
                .thenReturn(List.of(donation, open, closed, expired));

        List<PostConteudoDTO> visible = service.listarConteudoPublicoDaOsc(101);

        assertEquals(2, visible.size());
        assertFalse(visible.stream().anyMatch(item -> item.id().equals(closed.getId())));
        assertFalse(visible.stream().anyMatch(item -> item.id().equals(expired.getId())));
        assertTrue(visible.stream().allMatch(item -> !item.podeEditar()));
    }

    @Test
    void homepagePublicationPageIsCappedAtSixAndReturnsPublicReadOnlyCards() {
        Post publication = post(TipoPost.DOACAO, 7);
        publication.setTitulo("Campanha pública");
        osc.setNome("ONG de teste");
        PageRequest expected = PageRequest.of(2, 6);
        when(postRepository.findPublicacoesHome(StatusOsc.ATIVA, TipoPost.VOLUNTARIADO, expected))
                .thenReturn(new PageImpl<>(List.of(publication), expected, 13));

        Page<PostConteudoDTO> result = service.listarPublicacoesHome(PageRequest.of(2, 100));

        assertEquals(2, result.getNumber());
        assertEquals(6, result.getSize());
        assertEquals(13, result.getTotalElements());
        assertEquals(TipoPost.DOACAO, result.getContent().get(0).tipo());
        assertEquals("ONG de teste", result.getContent().get(0).nomeOsc());
        assertFalse(result.getContent().get(0).podeEditar());
        verify(postRepository).findPublicacoesHome(StatusOsc.ATIVA, TipoPost.VOLUNTARIADO, expected);
    }

    @Test
    void catalogPageIsSeparatelyBoundedAtTwentyFourWithoutChangingHomepageContract() {
        PageRequest expected = PageRequest.of(3, 24);
        when(postRepository.findPublicacoesHome(StatusOsc.ATIVA, TipoPost.VOLUNTARIADO, expected))
                .thenReturn(Page.empty(expected));

        Page<PostConteudoDTO> result = service.listarPublicacoesCatalogo(PageRequest.of(3, 500));

        assertEquals(3, result.getNumber());
        assertEquals(24, result.getSize());
        verify(postRepository).findPublicacoesHome(StatusOsc.ATIVA, TipoPost.VOLUNTARIADO, expected);
    }

    @Test
    void homepageVolunteerPageUsesOnlyOpenVacancyQueryAndIsReadOnly() {
        Post opportunity = post(TipoPost.VOLUNTARIADO, 7);
        Vaga vacancy = vacancy(StatusVaga.ABERTA, LocalDate.now().plusDays(3));
        opportunity.setVagaVoluntariado(vacancy);
        PageRequest expected = PageRequest.of(0, 6);
        when(postRepository.findVoluntariadosHome(
                StatusOsc.ATIVA, TipoPost.VOLUNTARIADO, StatusVaga.ABERTA, LocalDate.now(), expected))
                .thenReturn(new PageImpl<>(List.of(opportunity), expected, 1));

        Page<PostConteudoDTO> result = service.listarVoluntariadosHome(PageRequest.of(0, 6));

        assertEquals(1, result.getTotalElements());
        assertEquals(StatusVaga.ABERTA, result.getContent().get(0).vaga().status());
        assertFalse(result.getContent().get(0).podeEditar());
        verify(postRepository).findVoluntariadosHome(
                StatusOsc.ATIVA, TipoPost.VOLUNTARIADO, StatusVaga.ABERTA, LocalDate.now(), expected);
    }

    @Test
    void homepageRepositoryQueriesEnforcePublicVisibilityConditions() throws Exception {
        String publications = PostRepository.class.getMethod("findPublicacoesHome",
                        StatusOsc.class, TipoPost.class, org.springframework.data.domain.Pageable.class)
                .getAnnotation(Query.class).value();
        String volunteering = PostRepository.class.getMethod("findVoluntariadosHome",
                        StatusOsc.class, TipoPost.class, StatusVaga.class,
                        LocalDate.class, org.springframework.data.domain.Pageable.class)
                .getAnnotation(Query.class).value();

        assertTrue(publications.contains("p.ativo = TRUE"));
        assertTrue(publications.contains("p.tipo <> :tipoVoluntariado"));
        assertTrue(publications.contains("o.status = :statusOsc"));
        assertTrue(publications.contains("o.dataArquivamento IS NULL"));
        assertTrue(volunteering.contains("p.ativo = TRUE"));
        assertTrue(volunteering.contains("v.status = :statusVaga"));
        assertTrue(volunteering.contains("v.quantidadeVagas > 0"));
        assertTrue(volunteering.contains("v.dataFim >= :dataAtual"));
        assertTrue(volunteering.contains("o.dataArquivamento IS NULL"));
    }

    @Test
    void anOpportunityWithInvalidDateRangeIsRejectedBeforePersistence() {
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.CRIADOR);
        RequestVagaDTO invalid = new RequestVagaDTO(
                1, "PRESENCIAL", "Requisito", "4h", "Santos", "Centro",
                null, null, null, LocalDate.now().plusDays(5), LocalDate.now(), "ABERTA");

        assertThrows(ResponseStatusException.class,
                () -> service.createInOsc(101, createPost("VOLUNTARIADO", invalid), actor));
        verify(postRepository, never()).save(any(Post.class));
        verify(vagaRepository, never()).save(any(Vaga.class));
    }

    @Test
    void contentAndOpportunityMutationsUseTransactionalServiceBoundaries() throws Exception {
        assertTrue(PostService.class.getMethod("createInOsc", Integer.class,
                RequestCreatePostDTO.class, Usuario.class).isAnnotationPresent(Transactional.class));
        assertTrue(PostService.class.getMethod("atualizarConteudoDaOsc", Integer.class,
                RequestUpdatePostDTO.class, Usuario.class).isAnnotationPresent(Transactional.class));
        assertTrue(PostService.class.getMethod("excluirConteudoDaOsc", Integer.class,
                Integer.class, Usuario.class).isAnnotationPresent(Transactional.class));
    }

    private RequestCreatePostDTO createPost(String type, RequestVagaDTO vaga) {
        return new RequestCreatePostDTO(new RequestPostDTO(
                type, true, "Post ONG", null, "Texto de teste"), vaga);
    }

    private byte[] pngValido() {
        try {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (java.io.IOException erro) {
            throw new IllegalStateException(erro);
        }
    }

    private RequestUpdatePostDTO updateRequest(Integer id, String title) {
        return new RequestUpdatePostDTO(id,
                new RequestPostDTO("DOACAO", true, title, null, "Conteudo atualizado"), null);
    }

    private RequestVagaDTO vagaRequest(String status) {
        return new RequestVagaDTO(2, "HIBRIDO", "Disponibilidade", "4h por semana",
                "Santos", "Centro", "Rua A", "10", null,
                LocalDate.now(), LocalDate.now().plusDays(15), status);
    }

    private Post post(TipoPost tipo, Integer authorId) {
        Post post = new Post();
        ReflectionTestUtils.setField(post, "id", 900);
        post.setOsc(osc);
        post.setTipo(tipo);
        post.setAtivo(true);
        post.setTitulo("Original");
        post.setConteudo("Conteudo original");
        Usuario author = new Usuario();
        ReflectionTestUtils.setField(author, "id", authorId);
        post.setUsuario(author);
        return post;
    }

    private OscMembros member(CargoOsc cargo) {
        OscMembros member = new OscMembros();
        member.setCargoOsc(cargo);
        return member;
    }

    private Vaga vacancy(StatusVaga status, LocalDate end) {
        Vaga vaga = new Vaga();
        ReflectionTestUtils.setField(vaga, "id", 77);
        vaga.setStatus(status);
        vaga.setQuantidadeVagas(2);
        vaga.setModalidade(ModalidadeVoluntariado.PRESENCIAL);
        vaga.setRequisitos("Disponibilidade");
        vaga.setCargaHoraria("4h");
        vaga.setCidade("Santos");
        vaga.setBairro("Centro");
        vaga.setDataInicio(LocalDate.now());
        vaga.setDataFim(end);
        return vaga;
    }
}
