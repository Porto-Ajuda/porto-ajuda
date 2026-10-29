package com.portoajuda.aplicacao_osc.service;

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
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
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

    @Transactional
    public void create(RequestCreatePostDTO request, Usuario usuarioAuth){
        Usuario usuario = usuarioRepository.findByEmail(usuarioAuth.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        boolean isAdmin = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        boolean isOsc = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OSC"));

        Post post = new Post();
        if (isAdmin && request.post().tipo().equals("NOTICIA")){
            post.setUsuario(usuario);
            post.setTipo(TipoPost.valueOf(request.post().tipo()));
            post.setAtivo(request.post().ativo());
            post.setTitulo(request.post().titulo());
            post.setConteudo(request.post().conteudo());
            post.setImagemUrl(request.post().imagemUrl());
            post.setDataCriacao(LocalDateTime.now());
            postRepository.save(post);
        } else if (isOsc) {
            Osc osc = oscRepository.findByUsuarioId(usuario.getId())
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
        boolean isAdmin = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        boolean isOsc = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OSC"));

        Post post = postRepository.findById(request.id())
                .orElseThrow(() -> new IllegalArgumentException("Erro ao buscar post"));

        if(isOsc){
            Osc osc = oscRepository.findByUsuarioId(usuarioAuth.getId())
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
        } else if (isAdmin) {
            if (post.getTipo() == TipoPost.NOTICIA) {
                post.setAtivo(request.post().ativo());
                post.setTitulo(request.post().titulo());
                post.setConteudo(request.post().conteudo());
                post.setImagemUrl(request.post().imagemUrl());
                post.setDataAtualizacao(LocalDateTime.now());
                postRepository.save(post);
            }else {
                throw new AccessDeniedException(
                        "Admins só podem editar notícias"
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
        boolean isAdmin = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

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
            vagaRepository.findByPost(post)
                    .ifPresent(vagaRepository::delete);

            postRepository.delete(post);
            return;
        }

        Osc osc = oscRepository.findByUsuarioId(usuarioAuth.getId())
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

        boolean isAdmin = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        boolean isOsc = usuarioAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OSC"));

        if (isAdmin) {
            return postRepository.findByTipo(TipoPost.NOTICIA)
                    .stream()
                    .map(ResponsePostDTO::from)
                    .toList();
        }

        if (isOsc) {
            Osc osc = oscRepository.findByUsuarioId(usuarioAuth.getId())
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
        return postRepository.findByOscCausas(causaSocial)
                .stream()
                .map(ResponsePostDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ResponsePostDTO> getAll(Pageable pageable) {
        return postRepository.findAll(pageable)
                .map(ResponsePostDTO::from);
    }
}