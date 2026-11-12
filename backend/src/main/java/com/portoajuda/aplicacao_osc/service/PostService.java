package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminOperationAuditService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.dto.request.RequestCreatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestPostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestUpdatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestVagaDTO;
import com.portoajuda.aplicacao_osc.dto.response.PostConteudoDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponsePostDTO;
import com.portoajuda.aplicacao_osc.dto.response.VagaConteudoDTO;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.enums.ModalidadeVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClientException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PostService {
    private static final Logger log = LoggerFactory.getLogger(PostService.class);
    private final OscRepository oscRepository;
    private final UsuarioRepository usuarioRepository;
    private final PostRepository postRepository;
    private final VagaRepository vagaRepository;
    private final AdminAuthorizationService adminAuthorization;
    private final AdminOperationAuditService operationAudit;
    private final OscMembrosRepository membrosRepository;
    private final CandidaturasVoluntariadoRepository candidaturasRepository;
    private final OscService oscService;
    private final SupabaseStorageService storageService;

    @Transactional
    public void create(RequestCreatePostDTO request, Usuario usuarioAuth){
        create(request, usuarioAuth, null);
    }

    @Transactional
    public void create(RequestCreatePostDTO request, Usuario usuarioAuth, String adminReason){
        if (!isAdminSession(usuarioAuth)) {
            createInOsc(resolveSingleOscForLegacyCreate(usuarioAuth), request, usuarioAuth);
            return;
        }
        if (request == null || request.post() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dados do post.");
        }
        Usuario usuario = usuarioRepository.findByEmail(usuarioAuth.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario nao encontrado."));
        AdminActor actor = adminAuthorization.requirePermission(AdminPermission.CONTENT_PUBLISH);
        String reason = adminAuthorization.requireReason(adminReason);
        if (!request.post().tipo().equals("NOTICIA")) {
            throw new AccessDeniedException("A publicacao administrativa deve ser uma noticia.");
        }
        Post post = new Post();
        post.setUsuario(usuario);
        post.setTipo(TipoPost.valueOf(request.post().tipo()));
        post.setAtivo(request.post().ativo());
        post.setTitulo(request.post().titulo());
        post.setConteudo(request.post().conteudo());
        post.setImagemUrl(request.post().imagemUrl());
        post.setDataCriacao(LocalDateTime.now());
        postRepository.save(post);
        operationAudit.record(actor, AdminPermission.CONTENT_PUBLISH,
                "CONTENT_PUBLISHED", "POST", post.getId().toString(), reason,
                null, "type=NOTICIA;active=" + post.isAtivo());
    }

    @Transactional
    public PostConteudoDTO createInOsc(Integer oscId, RequestCreatePostDTO request, Usuario usuarioAuth) {
        return createInOsc(oscId, request, usuarioAuth, null);
    }

    @Transactional
    public PostConteudoDTO createInOsc(
            Integer oscId, RequestCreatePostDTO request, Usuario usuarioAuth, MultipartFile imagem) {
        Osc osc = buscarOscParaGestao(oscId, usuarioAuth);
        CargoOsc cargo = oscService.obterCargoUsuario(osc, usuarioAuth);
        if (request == null || request.post() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dados do post.");
        }
        TipoPost tipo = parsePostType(request.post().tipo());
        if (tipo == TipoPost.NOTICIA) {
            throw new AccessDeniedException("Noticia e conteudo exclusivo do painel da plataforma.");
        }
        if ((tipo == TipoPost.VOLUNTARIADO) != (request.vaga() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Post de voluntariado e dados da oportunidade devem ser enviados juntos.");
        }
        rejeitarUrlImagemFornecida(request.post().imagemUrl());
        validarPost(request.post());
        if (tipo == TipoPost.VOLUNTARIADO) {
            validarDadosVaga(request.vaga());
        }
        Usuario autor = usuarioRepository.findById(usuarioAuth.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "User not found."));

        Post post = criarPost(osc, autor, tipo, request.post());
        postRepository.save(post);
        Vaga vaga = null;
        if (tipo == TipoPost.VOLUNTARIADO) {
            vaga = criarVaga(post, request.vaga(), StatusVaga.ABERTA);
            vagaRepository.save(vaga);
        }
        if (imagem != null) {
            SupabaseStorageService.ArquivoEnviado enviada = enviarImagemConteudo(oscId, post, imagem);
            agendarLimpezaSeRollback(oscId, post, enviada.caminho());
            post.setImagemUrl(enviada.url());
            postRepository.save(post);
        }
        return conteudoDTO(post, vaga, podeModificar(post, osc, usuarioAuth, cargo));
    }

    @Transactional(readOnly = true)
    public List<PostConteudoDTO> listarConteudoDaOsc(Integer oscId, Usuario usuarioAuth) {
        Osc osc = buscarOscParaGestao(oscId, usuarioAuth);
        CargoOsc cargo = oscService.obterCargoUsuario(osc, usuarioAuth);
        return postRepository.findByOsc_IdOrderByDataCriacaoDesc(oscId).stream()
                .map(post -> conteudoDTO(post, post.getVagaVoluntariado(),
                        podeModificar(post, osc, usuarioAuth, cargo)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PostConteudoDTO buscarConteudoDaOsc(Integer oscId, Integer postId, Usuario usuarioAuth) {
        Osc osc = buscarOscParaGestao(oscId, usuarioAuth);
        CargoOsc cargo = oscService.obterCargoUsuario(osc, usuarioAuth);
        Post post = buscarPostDaOsc(osc, postId);
        return conteudoDTO(post, post.getVagaVoluntariado(),
                podeModificar(post, osc, usuarioAuth, cargo));
    }

    @Transactional
    public PostConteudoDTO atualizarConteudoDaOsc(
            Integer oscId, RequestUpdatePostDTO request, Usuario usuarioAuth) {
        return atualizarConteudoDaOsc(oscId, request, usuarioAuth, null);
    }

    @Transactional
    public PostConteudoDTO atualizarConteudoDaOsc(
            Integer oscId, RequestUpdatePostDTO request, Usuario usuarioAuth, MultipartFile imagem) {
        Osc osc = buscarOscParaGestao(oscId, usuarioAuth);
        CargoOsc cargo = oscService.obterCargoUsuario(osc, usuarioAuth);
        if (request == null || request.id() == null || request.post() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dados do post.");
        }
        Post post = buscarPostDaOsc(osc, request.id());
        if (!podeModificar(post, osc, usuarioAuth, cargo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seu cargo nao permite alterar este conteudo.");
        }
        if (post.getTipo() == TipoPost.NOTICIA) {
            throw new AccessDeniedException("Noticia e conteudo exclusivo do painel da plataforma.");
        }
        if (parsePostType(request.post().tipo()) != post.getTipo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O tipo de um post existente nao pode ser alterado.");
        }
        preservarOuRejeitarUrlImagem(post, request.post().imagemUrl());
        if ((post.getTipo() == TipoPost.VOLUNTARIADO) != (request.vaga() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Post de voluntariado e dados da oportunidade devem ser enviados juntos.");
        }
        validarPost(request.post());
        StatusVaga statusVaga = null;
        if (post.getTipo() == TipoPost.VOLUNTARIADO) {
            validarDadosVaga(request.vaga());
            statusVaga = request.vaga().status() == null || request.vaga().status().isBlank()
                    ? null : parseVagaStatus(request.vaga().status());
        }
        Vaga vaga = null;
        if (post.getTipo() == TipoPost.VOLUNTARIADO) {
            Vaga existente = vagaRepository.findByPost(post).orElse(null);
            vaga = existente == null
                    ? new Vaga()
                    : vagaRepository.findForUpdateById(existente.getId()).orElseThrow(() ->
                            new ResponseStatusException(HttpStatus.NOT_FOUND, "Oportunidade não encontrada."));
        }
        post.setAtivo(request.post().ativo());
        post.setTitulo(request.post().titulo().trim());
        post.setConteudo(request.post().conteudo().trim());
        String imagemAnterior = post.getImagemUrl();
        post.setDataAtualizacao(LocalDateTime.now());
        postRepository.save(post);

        if (post.getTipo() == TipoPost.VOLUNTARIADO) {
            StatusVaga status = statusVaga == null
                    ? (vaga.getStatus() == null ? StatusVaga.ABERTA : vaga.getStatus())
                    : statusVaga;
            preencherVaga(vaga, post, request.vaga(), status);
            vagaRepository.save(vaga);
        }
        if (imagem != null) {
            SupabaseStorageService.ArquivoEnviado enviada = enviarImagemConteudo(oscId, post, imagem);
            agendarLimpezaSeRollback(oscId, post, enviada.caminho());
            post.setImagemUrl(enviada.url());
            postRepository.save(post);
            if (!Objects.equals(imagemAnterior, enviada.url())) {
                agendarLimpezaImagemSeNaoReferenciada(oscId, post, imagemAnterior);
            }
        }
        return conteudoDTO(post, vaga, true);
    }

    @Transactional(readOnly = true)
    public List<PostConteudoDTO> listarConteudoPublicoDaOsc(Integer oscId) {
        Osc osc = oscRepository.findById(oscId)
                .filter(org -> !org.isArquivada() && org.getStatus() == StatusOsc.ATIVA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Organizacao nao encontrada."));
        return postRepository.findPublicosPorOsc(osc.getId(), StatusOsc.ATIVA).stream()
                .filter(this::publicamenteVisivel)
                .map(post -> conteudoDTO(post, post.getVagaVoluntariado(), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public PostConteudoDTO buscarConteudoPublicoDaOsc(Integer oscId, Integer postId) {
        return listarConteudoPublicoDaOsc(oscId).stream()
                .filter(conteudo -> Objects.equals(conteudo.id(), postId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Conteudo nao encontrado."));
    }

    @Transactional
    public void excluirConteudoDaOsc(Integer oscId, Integer postId, Usuario usuarioAuth) {
        Osc osc = buscarOscParaGestao(oscId, usuarioAuth);
        CargoOsc cargo = oscService.obterCargoUsuario(osc, usuarioAuth);
        Post post = buscarPostDaOsc(osc, postId);
        if (!podeModificar(post, osc, usuarioAuth, cargo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seu cargo nao permite excluir este conteudo.");
        }
        String imagemAnterior = post.getImagemUrl();
        excluirPostEPreservarCandidaturas(post);
        agendarLimpezaImagemSeNaoReferenciada(oscId, post, imagemAnterior);
    }

    @Transactional
    public void update(RequestUpdatePostDTO request, Usuario usuarioAuth){
        update(request, usuarioAuth, null);
    }

    @Transactional
    public void update(RequestUpdatePostDTO request, Usuario usuarioAuth, String adminReason){
        if (isAdminSession(usuarioAuth)) {
            if (request == null || request.id() == null || request.post() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dados do post.");
            }
            AdminActor actor = adminAuthorization.requirePermission(AdminPermission.CONTENT_EDIT_NEWS);
            String reason = adminAuthorization.requireReason(adminReason);
            Post post = postRepository.findById(request.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post nÃ£o encontrado."));
            if (post.getTipo() != TipoPost.NOTICIA) {
                throw new AccessDeniedException("Administradores so podem editar noticias nesta rota.");
            }
            boolean previousActive = post.isAtivo();
            post.setAtivo(request.post().ativo());
            post.setTitulo(request.post().titulo());
            post.setConteudo(request.post().conteudo());
            post.setImagemUrl(request.post().imagemUrl());
            post.setDataAtualizacao(LocalDateTime.now());
            postRepository.save(post);
            operationAudit.record(actor, AdminPermission.CONTENT_EDIT_NEWS,
                    "CONTENT_NEWS_EDITED", "POST", post.getId().toString(), reason,
                    "active=" + previousActive, "active=" + post.isAtivo());
            return;
        }
        rejeitarRoleAdminSemSessaoOsc();
        if (request == null || request.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o post que deseja alterar.");
        }
        Post post = postRepository.findById(request.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post nÃ£o encontrado."));
        if (post.getOsc() == null || post.getOsc().getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Este post nÃ£o pertence a uma organizaÃ§Ã£o gerenciÃ¡vel por membros.");
        }
        atualizarConteudoDaOsc(post.getOsc().getId(), request, usuarioAuth);
    }
    @Transactional
    public void delete(Integer id, Usuario usuarioAuth) {
        delete(id, usuarioAuth, null);
    }

    @Transactional
    public void delete(Integer id, Usuario usuarioAuth, String adminReason) {
        if (isAdminSession(usuarioAuth)) {
            AdminActor actor = adminAuthorization.requirePermission(AdminPermission.CONTENT_DELETE);
            String reason = adminAuthorization.requireReason(adminReason);
            Post post = postRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post nÃ£o encontrado."));
            TipoPost previousType = post.getTipo();
            Vaga vaga = vagaRepository.findByPost(post).orElse(null);
            boolean hadVolunteerOpportunity = vaga != null;
            if (vaga != null && vaga.getId() != null && candidaturasRepository.existsByVaga_Id(vaga.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Esta oportunidade possui candidaturas e nao pode ser excluida.");
            }
            if (vaga != null) {
                vagaRepository.delete(vaga);
            }
            postRepository.delete(post);
            operationAudit.record(actor, AdminPermission.CONTENT_DELETE,
                    "CONTENT_PHYSICALLY_DELETED", "POST", id.toString(), reason,
                    "type=" + previousType + ";volunteerOpportunity=" + hadVolunteerOpportunity,
                    "deleted=true");
            return;
        }
        rejeitarRoleAdminSemSessaoOsc();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post nÃ£o encontrado."));
        if (post.getOsc() == null || post.getOsc().getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Este post nÃ£o pertence a uma organizaÃ§Ã£o gerenciÃ¡vel por membros.");
        }
        excluirConteudoDaOsc(post.getOsc().getId(), id, usuarioAuth);
    }
    @Transactional(readOnly = true)
    public List<ResponsePostDTO> getMeusPosts(Usuario usuarioAuth) {
        if (isAdminSession(usuarioAuth)) {
            adminAuthorization.requirePermission(AdminPermission.CONTENT_READ);
            return postRepository.findByTipo(TipoPost.NOTICIA).stream()
                    .map(ResponsePostDTO::from)
                    .toList();
        }
        exigirUsuario(usuarioAuth);
        return membrosRepository.findAllByUsuario_IdOrderByOsc_NomeAsc(usuarioAuth.getId()).stream()
                .map(OscMembros::getOsc)
                .filter(osc -> osc != null && !osc.isArquivada())
                .distinct()
                .flatMap(osc -> {
                    oscService.obterCargoUsuario(osc, usuarioAuth);
                    return postRepository.findByOsc_IdOrderByDataCriacaoDesc(osc.getId()).stream();
                })
                .map(ResponsePostDTO::from)
                .toList();
    }
    @Transactional(readOnly = true)
    public List<ResponsePostDTO> getPostsPorCausaSocial(CausaSocial causaSocial) {
        return postRepository.findPublicosPorCausaSocial(causaSocial, StatusOsc.ATIVA)
                .stream()
                .map(ResponsePostDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ResponsePostDTO> getAll(Pageable pageable) {
        return postRepository.findPublicos(StatusOsc.ATIVA, pageable)
                .map(ResponsePostDTO::from);
    }

    @Transactional(readOnly = true)
    public Page<PostConteudoDTO> listarPublicacoesHome(Pageable pageable) {
        Pageable vitrine = pageableVitrine(pageable);
        return postRepository.findPublicacoesHome(StatusOsc.ATIVA, TipoPost.VOLUNTARIADO, vitrine)
                .map(post -> conteudoDTO(post, post.getVagaVoluntariado(), false));
    }

    @Transactional(readOnly = true)
    public Page<PostConteudoDTO> listarVoluntariadosHome(Pageable pageable) {
        Pageable vitrine = pageableVitrine(pageable);
        return postRepository.findVoluntariadosHome(
                        StatusOsc.ATIVA, TipoPost.VOLUNTARIADO, StatusVaga.ABERTA, LocalDate.now(), vitrine)
                .map(post -> conteudoDTO(post, post.getVagaVoluntariado(), false));
    }

    @Transactional(readOnly = true)
    public Page<PostConteudoDTO> listarPublicacoesCatalogo(Pageable pageable) {
        Pageable catalogo = pageableCatalogo(pageable);
        return postRepository.findPublicacoesHome(StatusOsc.ATIVA, TipoPost.VOLUNTARIADO, catalogo)
                .map(post -> conteudoDTO(post, post.getVagaVoluntariado(), false));
    }

    @Transactional(readOnly = true)
    public Page<PostConteudoDTO> listarVoluntariadosCatalogo(Pageable pageable) {
        Pageable catalogo = pageableCatalogo(pageable);
        return postRepository.findVoluntariadosHome(
                        StatusOsc.ATIVA, TipoPost.VOLUNTARIADO, StatusVaga.ABERTA, LocalDate.now(), catalogo)
                .map(post -> conteudoDTO(post, post.getVagaVoluntariado(), false));
    }

    private Pageable pageableVitrine(Pageable pageable) {
        int numero = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        return PageRequest.of(numero, Math.min(pageable == null ? 6 : pageable.getPageSize(), 6));
    }

    private Pageable pageableCatalogo(Pageable pageable) {
        int numero = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        return PageRequest.of(numero, Math.min(pageable == null ? 24 : pageable.getPageSize(), 24));
    }

    private Integer resolveSingleOscForLegacyCreate(Usuario usuario) {
        exigirUsuario(usuario);
        List<Osc> autorizadas = new ArrayList<>();
        for (OscMembros membro : membrosRepository.findAllByUsuario_IdOrderByOsc_NomeAsc(usuario.getId())) {
            Osc osc = membro.getOsc();
            if (osc == null || osc.getId() == null || osc.isArquivada()) {
                continue;
            }
            oscService.obterCargoUsuario(osc, usuario);
            if (autorizadas.stream().noneMatch(existente -> existente.getId().equals(osc.getId()))) {
                autorizadas.add(osc);
            }
        }
        if (autorizadas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Voce nao pertence a uma organizacao ativa para gerenciar conteudo.");
        }
        if (autorizadas.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecione a organizacao de destino para criar conteudo.");
        }
        return autorizadas.getFirst().getId();
    }

    private Osc buscarOscParaGestao(Integer oscId, Usuario usuario) {
        exigirUsuario(usuario);
        if (oscId == null || oscId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de organizacao invalido.");
        }
        Osc osc = oscRepository.findById(oscId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Organizacao nao encontrada."));
        if (osc.isArquivada()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada.");
        }
        // Inactive organizations remain manageable by their actual members.
        return osc;
    }

    private void exigirUsuario(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria.");
        }
    }

    private Post buscarPostDaOsc(Osc osc, Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post nao encontrado."));
        if (post.getOsc() == null || !Objects.equals(post.getOsc().getId(), osc.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post nao encontrado nesta organizacao.");
        }
        return post;
    }

    private boolean podeModificar(Post post, Osc osc, Usuario ator, CargoOsc cargoAtor) {
        if (post.getTipo() == TipoPost.NOTICIA) {
            return false;
        }
        Integer autorId = post.getUsuario() == null ? null : post.getUsuario().getId();
        if (autorId != null && autorId.equals(ator.getId())) {
            return true;
        }
        if (autorId == null) {
            return cargoAtor == CargoOsc.CRIADOR;
        }
        CargoOsc cargoAutor = membrosRepository.findByOsc_IdAndUsuario_Id(osc.getId(), autorId)
                .map(OscMembros::getCargoOsc)
                .orElse(null);
        if (cargoAutor == null) {
            // The current schema has no author-role snapshot. Unknown authors are protected
            // from lower roles; only the top NGO role may take over this content.
            return cargoAtor == CargoOsc.CRIADOR;
        }
        return nivel(cargoAtor) >= nivel(cargoAutor);
    }

    private int nivel(CargoOsc cargo) {
        return switch (cargo) {
            case CRIADOR -> 4;
            case ADMINISTRADOR -> 3;
            case EDITOR -> 2;
            case MEMBRO -> 1;
        };
    }

    private TipoPost parsePostType(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o tipo do post.");
        }
        try {
            return TipoPost.fromString(valor.trim());
        } catch (IllegalArgumentException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de post invalido.");
        }
    }

    private StatusVaga parseVagaStatus(String valor) {
        try {
            return StatusVaga.fromString(valor.trim());
        } catch (RuntimeException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de oportunidade invalido.");
        }
    }

    private ModalidadeVoluntariado parseModalidade(String valor) {
        try {
            return ModalidadeVoluntariado.fromString(valor.trim());
        } catch (RuntimeException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modalidade de voluntariado invalida.");
        }
    }

    private void validarPost(RequestPostDTO post) {
        if (post.titulo() == null || post.titulo().isBlank() || post.titulo().trim().length() > 255
                || post.conteudo() == null || post.conteudo().isBlank() || post.conteudo().trim().length() > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe titulo e conteudo dentro dos limites permitidos.");
        }
    }

    private void rejeitarUrlImagemFornecida(String imagemUrl) {
        if (imagemUrl != null && !imagemUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Envie uma imagem JPEG ou PNG pelo campo de arquivo.");
        }
    }

    private void preservarOuRejeitarUrlImagem(Post post, String imagemUrl) {
        if (imagemUrl == null || imagemUrl.isBlank()) return;
        if (!Objects.equals(imagemUrl.trim(), post.getImagemUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A imagem deve ser enviada como arquivo; URLs fornecidas não são aceitas.");
        }
    }

    private SupabaseStorageService.ArquivoEnviado enviarImagemConteudo(
            Integer oscId, Post post, MultipartFile imagem) {
        try {
            if (imagem == null || imagem.isEmpty() || imagem.getSize() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma imagem válida.");
            }
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("O upload de conteúdo exige uma transação ativa.");
            }
            if (imagem.getSize() > 10L * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A imagem não pode ultrapassar 10 MB.");
            }
            return storageService.enviarImagemConteudoOsc(oscId, post.getId(),
                    post.getTipo() == TipoPost.VOLUNTARIADO, imagem.getBytes(), imagem.getContentType());
        } catch (IllegalArgumentException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, erro.getMessage());
        } catch (IOException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não foi possível ler o arquivo enviado.");
        } catch (RestClientException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível armazenar a imagem agora. Tente novamente.");
        }
    }

    private void agendarLimpezaSeRollback(Integer oscId, Post post, String caminho) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            removerImagemAposFalha(oscId, post, caminho);
            throw new IllegalStateException("O upload de conteúdo exige uma transação ativa.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    removerImagemAposFalha(oscId, post, caminho);
                }
            }
        });
    }

    private void removerImagemAposFalha(Integer oscId, Post post, String caminho) {
        try {
            storageService.removerImagemConteudoOsc(oscId, post.getId(),
                    post.getTipo() == TipoPost.VOLUNTARIADO, caminho);
        } catch (RuntimeException erro) {
            log.warn("Falha ao limpar imagem de conteúdo após rollback; oscId={}, postId={}",
                    oscId, post.getId());
        }
    }

    private void agendarLimpezaImagemSeNaoReferenciada(Integer oscId, Post post, String imagemAnterior) {
        if (imagemAnterior == null || imagemAnterior.isBlank()
                || !TransactionSynchronizationManager.isSynchronizationActive()) return;
        boolean voluntariado = post.getTipo() == TipoPost.VOLUNTARIADO;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) return;
                if (postRepository.existsByImagemUrlAndIdNot(imagemAnterior, post.getId())) return;
                try {
                    storageService.removerImagemConteudoOscUrl(
                            oscId, post.getId(), voluntariado, imagemAnterior);
                } catch (RuntimeException erro) {
                    log.warn("Falha ao remover imagem sem referências de conteúdo; oscId={}, postId={}",
                            oscId, post.getId());
                }
            }
        });
    }

    private String normalizarImagem(String imagemUrl) {
        if (imagemUrl == null || imagemUrl.isBlank()) {
            return null;
        }
        String valor = imagemUrl.trim();
        if (valor.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A URL da imagem excede o limite permitido.");
        }
        try {
            URI uri = URI.create(valor);
            String scheme = uri.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                return valor;
            }
            if (scheme == null && valor.startsWith("/") && !valor.startsWith("//")) {
                return valor;
            }
        } catch (IllegalArgumentException ignored) {
            // Use a generic validation error; do not echo untrusted input.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma URL de imagem valida.");
    }

    private Post criarPost(Osc osc, Usuario autor, TipoPost tipo, RequestPostDTO request) {
        Post post = new Post();
        post.setOsc(osc);
        post.setUsuario(autor);
        post.setTipo(tipo);
        post.setAtivo(request.ativo());
        post.setTitulo(request.titulo().trim());
        post.setConteudo(request.conteudo().trim());
        post.setImagemUrl(null);
        post.setDataCriacao(LocalDateTime.now());
        return post;
    }

    private Vaga criarVaga(Post post, RequestVagaDTO request, StatusVaga status) {
        Vaga vaga = new Vaga();
        preencherVaga(vaga, post, request, status);
        return vaga;
    }

    private void preencherVaga(Vaga vaga, Post post, RequestVagaDTO request, StatusVaga status) {
        validarDadosVaga(request);
        vaga.setPost(post);
        vaga.setQuantidadeVagas(request.quantidadeVagas());
        vaga.setModalidade(parseModalidade(request.modalidadeVoluntariado()));
        vaga.setRequisitos(request.requisitos().trim());
        vaga.setCargaHoraria(request.cargaHoraria().trim());
        vaga.setCidade(request.cidade().trim());
        vaga.setBairro(request.bairro().trim());
        vaga.setRua(limitarOpcional(request.rua(), 100));
        vaga.setNumero(limitarOpcional(request.numero(), 100));
        vaga.setComplemento(limitarOpcional(request.complemento(), 100));
        vaga.setDataInicio(request.dataInicio());
        vaga.setDataFim(request.dataFim());
        vaga.setStatus(status);
    }

    private void validarDadosVaga(RequestVagaDTO request) {
        if (request == null || request.quantidadeVagas() < 1
                || request.requisitos() == null || request.requisitos().isBlank()
                || request.cargaHoraria() == null || request.cargaHoraria().isBlank()
                || request.cargaHoraria().trim().length() > 100
                || request.cidade() == null || request.cidade().isBlank()
                || request.cidade().trim().length() > 100
                || request.bairro() == null || request.bairro().isBlank()
                || request.bairro().trim().length() > 100
                || request.dataInicio() == null || request.dataFim() == null
                || request.dataFim().isBefore(request.dataInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Revise os campos obrigatorios e as datas da oportunidade.");
        }
        parseModalidade(request.modalidadeVoluntariado());
        limitarOpcional(request.rua(), 100);
        limitarOpcional(request.numero(), 100);
        limitarOpcional(request.complemento(), 100);
    }

    private String limitarOpcional(String valor, int limite) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.trim();
        if (normalizado.length() > limite) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Um campo da oportunidade excede o limite permitido.");
        }
        return normalizado;
    }

    private PostConteudoDTO conteudoDTO(Post post, Vaga vaga, boolean podeEditar) {
        Vaga vinculada = vaga != null ? vaga : post.getVagaVoluntariado();
        if (vinculada == null && post.getTipo() == TipoPost.VOLUNTARIADO) {
            vinculada = vagaRepository.findByPost(post).orElse(null);
        }
        return PostConteudoDTO.from(post, VagaConteudoDTO.from(vinculada), podeEditar);
    }

    private boolean publicamenteVisivel(Post post) {
        if (!post.isAtivo()) {
            return false;
        }
        if (post.getTipo() != TipoPost.VOLUNTARIADO) {
            return true;
        }
        Vaga vaga = post.getVagaVoluntariado();
        if (vaga == null) {
            vaga = vagaRepository.findByPost(post).orElse(null);
        }
        return vaga != null && vaga.getStatus() == StatusVaga.ABERTA
                && vaga.getQuantidadeVagas() != null && vaga.getQuantidadeVagas() > 0
                && vaga.getDataFim() != null && !vaga.getDataFim().isBefore(LocalDate.now());
    }

    private void excluirPostEPreservarCandidaturas(Post post) {
        Vaga vaga = vagaRepository.findByPost(post).orElse(null);
        if (vaga != null && vaga.getId() != null && candidaturasRepository.existsByVaga_Id(vaga.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta oportunidade possui candidaturas; encerre-a para preservar o historico.");
        }
        if (vaga != null) {
            vagaRepository.delete(vaga);
        }
        postRepository.delete(post);
    }

    private boolean isAdminSession(Usuario usuario) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return usuario != null && authentication != null
                && usuario.getId() != null
                && usuario.getId().equals(
                        authentication.getPrincipal() instanceof Usuario principal
                                ? principal.getId()
                                : null)
                && adminAuthorization.hasValidPrivilegedSession(authentication);
    }

    private void rejeitarRoleAdminSemSessaoOsc() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Autenticacao necessaria.");
        }
        boolean roleAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        boolean roleOsc = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OSC"));
        if (roleAdmin && !roleOsc) {
            throw new AccessDeniedException("ROLE_ADMIN sem sessao administrativa nao concede acesso.");
        }
    }
}
