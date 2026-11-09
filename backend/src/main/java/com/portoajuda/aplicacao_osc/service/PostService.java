package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminOperationAuditService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.dto.request.RequestCreatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestPostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestUpdatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestVagaDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponsePostDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.enums.ModalidadeVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final OscRepository oscRepository;
    private final UsuarioRepository usuarioRepository;
    private final PostRepository postRepository;
    private final VagaRepository vagaRepository;
    private final AdminAuthorizationService adminAuthorization;
    private final AdminOperationAuditService operationAudit;

    @Transactional
    public void create(RequestCreatePostDTO request, Usuario usuarioAuth){
        create(request, usuarioAuth, null);
    }

    @Transactional
    public void create(RequestCreatePostDTO request, Usuario usuarioAuth, String adminReason){
        Usuario usuario = usuarioRepository.findByEmail(usuarioAuth.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        boolean isAdmin = isAdminSession(usuarioAuth);

        boolean isOsc = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OSC"));

        Post post = new Post();
        if (isAdmin) {
            AdminActor actor = adminAuthorization.requirePermission(AdminPermission.CONTENT_PUBLISH);
            String reason = adminAuthorization.requireReason(adminReason);
            if (!request.post().tipo().equals("NOTICIA")) {
                throw new AccessDeniedException("A publicação administrativa deve ser uma notícia.");
            }
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
            return;
        }
        if (isOsc) {
            Osc osc = oscRepository.findByUsuarioIdAndDataArquivamentoIsNull(usuario.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Erro ao encontrar OSC"));
            if(request.post().tipo().equals("DOACAO")){
                post.setOsc(osc);
                post.setUsuario(usuario);
                post.setTipo(TipoPost.valueOf(request.post().tipo()));
                post.setAtivo(request.post().ativo());
                post.setTitulo(request.post().titulo());
                post.setConteudo(request.post().conteudo());
                post.setImagemUrl(request.post().imagemUrl());
                post.setDataCriacao(LocalDateTime.now());
                postRepository.save(post);
            } else if (request.post().tipo().equals("VOLUNTARIADO")) {
                if (request.vaga() == null) {
                    throw new IllegalArgumentException(
                            "Os dados da vaga são obrigatórios para posts de voluntariado"
                    );
                }
                post.setOsc(osc);
                post.setUsuario(usuario);
                post.setTipo(TipoPost.valueOf(request.post().tipo()));
                post.setAtivo(request.post().ativo());
                post.setTitulo(request.post().titulo());
                post.setConteudo(request.post().conteudo());
                post.setImagemUrl(request.post().imagemUrl());
                post.setDataCriacao(LocalDateTime.now());
                postRepository.save(post);

                Vaga vaga = new Vaga();
                vaga.setPost(post);
                vaga.setQuantidadeVagas(request.vaga().quantidadeVagas());
                vaga.setModalidade(ModalidadeVoluntariado.valueOf(request.vaga().modalidadeVoluntariado()));
                vaga.setRequisitos(request.vaga().requisitos());
                vaga.setCargaHoraria(request.vaga().cargaHoraria());
                vaga.setCidade(request.vaga().cidade());
                vaga.setBairro(request.vaga().bairro());
                vaga.setRua(request.vaga().rua());
                vaga.setNumero(request.vaga().numero());
                vaga.setComplemento(request.vaga().complemento());
                vaga.setDataInicio(request.vaga().dataInicio());
                vaga.setDataFim(request.vaga().dataFim());
                vaga.setStatus(StatusVaga.valueOf("ABERTA"));
                vagaRepository.save(vaga);
            }

        }else {
            throw new AccessDeniedException(
                    "Usuário não possui permissão para criar este post"
            );
        }
    }

    @Transactional
    public void update(RequestUpdatePostDTO request, Usuario usuarioAuth){
        update(request, usuarioAuth, null);
    }

    @Transactional
    public void update(RequestUpdatePostDTO request, Usuario usuarioAuth, String adminReason){
        boolean isAdmin = isAdminSession(usuarioAuth);

        if (isAdmin) {
            AdminActor actor = adminAuthorization.requirePermission(AdminPermission.CONTENT_EDIT_NEWS);
            String reason = adminAuthorization.requireReason(adminReason);
            Post post = postRepository.findById(request.id())
                    .orElseThrow(() -> new IllegalArgumentException("Erro ao buscar post"));
            if (post.getTipo() != TipoPost.NOTICIA) {
                throw new AccessDeniedException("Administradores só podem editar notícias nesta rota.");
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

        boolean isOsc = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OSC"));
        Post post = postRepository.findById(request.id())
                .orElseThrow(() -> new IllegalArgumentException("Erro ao buscar post"));

        if(isOsc){
            Osc osc = oscRepository.findByUsuarioIdAndDataArquivamentoIsNull(usuarioAuth.getId())
                    .orElseThrow(() -> new IllegalArgumentException("OSC não encontrada"));
            if (post.getOsc() == null ||
                    !post.getOsc().getId().equals(osc.getId())) {
                throw new AccessDeniedException(
                        "OSC não possui permissão para editar este post"
                );
            }
            if(post.getTipo() == TipoPost.DOACAO){
                post.setAtivo(request.post().ativo());
                post.setTitulo(request.post().titulo());
                post.setConteudo(request.post().conteudo());
                post.setImagemUrl(request.post().imagemUrl());
                post.setDataAtualizacao(LocalDateTime.now());
                postRepository.save(post);
            } else if (post.getTipo() == TipoPost.VOLUNTARIADO) {
                post.setAtivo(request.post().ativo());
                post.setTitulo(request.post().titulo());
                post.setConteudo(request.post().conteudo());
                post.setImagemUrl(request.post().imagemUrl());
                post.setDataAtualizacao(LocalDateTime.now());
                postRepository.save(post);

                if (request.vaga() != null) {
                    Vaga vaga = vagaRepository.findByPost(post)
                            .orElseThrow(() ->
                                    new IllegalArgumentException("Vaga não encontrada"));

                    vaga.setQuantidadeVagas(request.vaga().quantidadeVagas());
                    vaga.setModalidade(
                            ModalidadeVoluntariado.valueOf(
                                    request.vaga().modalidadeVoluntariado()));
                    vaga.setRequisitos(request.vaga().requisitos());
                    vaga.setCargaHoraria(request.vaga().cargaHoraria());
                    vaga.setCidade(request.vaga().cidade());
                    vaga.setBairro(request.vaga().bairro());
                    vaga.setRua(request.vaga().rua());
                    vaga.setNumero(request.vaga().numero());
                    vaga.setComplemento(request.vaga().complemento());
                    vaga.setDataInicio(request.vaga().dataInicio());
                    vaga.setDataFim(request.vaga().dataFim());
                    vaga.setStatus(
                            StatusVaga.valueOf(request.vaga().status()));

                    vagaRepository.save(vaga);
                }
            } else {
                throw new AccessDeniedException(
                        "OSC não possui permissão para editar este tipo de post"
                );
            }
        } else {
            throw new AccessDeniedException(
                    "Usuário não possui permissão para editar posts"
            );
        }
    }

    @Transactional
    public void delete(Integer id, Usuario usuarioAuth) {
        delete(id, usuarioAuth, null);
    }

    @Transactional
    public void delete(Integer id, Usuario usuarioAuth, String adminReason) {
        boolean isAdmin = isAdminSession(usuarioAuth);

        boolean isOsc = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OSC"));

        if (!isAdmin && !isOsc) {
            throw new AccessDeniedException(
                    "Usuário não possui permissão para excluir posts"
            );
        }

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Post não encontrado"
                ));

        if (isAdmin) {
            AdminActor actor = adminAuthorization.requirePermission(AdminPermission.CONTENT_DELETE);
            String reason = adminAuthorization.requireReason(adminReason);
            TipoPost previousType = post.getTipo();
            boolean hadVolunteerOpportunity = vagaRepository.findByPost(post).isPresent();
            vagaRepository.findByPost(post)
                    .ifPresent(vagaRepository::delete);

            postRepository.delete(post);
            operationAudit.record(actor, AdminPermission.CONTENT_DELETE,
                    "CONTENT_PHYSICALLY_DELETED", "POST", id.toString(), reason,
                    "type=" + previousType + ";volunteerOpportunity=" + hadVolunteerOpportunity,
                    "deleted=true");
            return;
        }

        Osc osc = oscRepository.findByUsuarioIdAndDataArquivamentoIsNull(usuarioAuth.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "OSC não encontrada"
                ));

        if (post.getOsc() == null ||
                !post.getOsc().getId().equals(osc.getId())) {
            throw new AccessDeniedException(
                    "OSC não possui permissão para excluir este post"
            );
        }

        if (post.getTipo() == TipoPost.NOTICIA) {
            throw new AccessDeniedException(
                    "OSC só pode excluir posts de doação ou voluntariado"
            );
        }

        vagaRepository.findByPost(post)
                .ifPresent(vagaRepository::delete);

        postRepository.delete(post);
    }

    //fazer DTO
    @Transactional(readOnly = true)
    public List<ResponsePostDTO> getMeusPosts(Usuario usuarioAuth) {

        boolean isAdmin = isAdminSession(usuarioAuth);

        boolean isOsc = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OSC"));

        if (isAdmin) {
            adminAuthorization.requirePermission(AdminPermission.CONTENT_READ);
            return postRepository.findByTipo(TipoPost.NOTICIA)
                    .stream()
                    .map(ResponsePostDTO::from)
                    .toList();
        }

        if (isOsc) {
            Osc osc = oscRepository.findByUsuarioIdAndDataArquivamentoIsNull(usuarioAuth.getId())
                    .orElseThrow(() ->
                            new IllegalArgumentException("OSC não encontrada"));

            return postRepository.findByOscId(osc.getId())
                    .stream()
                    .map(ResponsePostDTO::from)
                    .toList();
        }

        throw new AccessDeniedException(
                "Usuário não possui permissão para consultar posts"
        );
    }

    //fazer DTO
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
}
