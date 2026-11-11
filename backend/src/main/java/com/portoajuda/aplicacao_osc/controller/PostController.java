package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.RequestCreatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestUpdatePostDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponsePostDTO;
import com.portoajuda.aplicacao_osc.dto.response.PostConteudoDTO;
import com.portoajuda.aplicacao_osc.dto.response.PaginaVitrineDTO;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/post")
public class PostController {
    private final PostService postService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping(
            value = "/create",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> create(@Valid @RequestBody RequestCreatePostDTO request, @AuthenticationPrincipal Usuario usuarioAuth){
        postService.create(
                request,
                usuarioAuth);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping(
            value = "/put",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> put(@Valid @RequestBody RequestUpdatePostDTO request, @AuthenticationPrincipal Usuario usuarioAuth){
        postService.update(
                request,
                usuarioAuth);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping(
            value = "/delete",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> delete(@Valid @RequestBody Integer id, @AuthenticationPrincipal Usuario usuarioAuth){
        postService.delete(
                id,
                usuarioAuth);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/meus-posts")
    public ResponseEntity<List<ResponsePostDTO>> getMeusPosts(
            @AuthenticationPrincipal Usuario usuarioAuth) {

        return ResponseEntity.ok(
                postService.getMeusPosts(usuarioAuth)
        );
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/osc/{oscId}/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PostConteudoDTO> createForOsc(
            @PathVariable Integer oscId,
            @Valid @RequestBody RequestCreatePostDTO request,
            @AuthenticationPrincipal Usuario usuarioAuth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.createInOsc(oscId, request, usuarioAuth));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/osc/{oscId}/create-com-imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostConteudoDTO> createForOscWithImage(
            @PathVariable Integer oscId,
            @Valid @RequestPart("dados") RequestCreatePostDTO request,
            @RequestPart(value = "imagem", required = false) MultipartFile imagem,
            @AuthenticationPrincipal Usuario usuarioAuth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.createInOsc(oscId, request, usuarioAuth, imagem));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/osc/{oscId}/gestao", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PostConteudoDTO>> listForOsc(
            @PathVariable Integer oscId,
            @AuthenticationPrincipal Usuario usuarioAuth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(postService.listarConteudoDaOsc(oscId, usuarioAuth));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/osc/{oscId}/gestao/{postId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PostConteudoDTO> getForOsc(
            @PathVariable Integer oscId,
            @PathVariable Integer postId,
            @AuthenticationPrincipal Usuario usuarioAuth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(postService.buscarConteudoDaOsc(oscId, postId, usuarioAuth));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/osc/{oscId}/put", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PostConteudoDTO> updateForOsc(
            @PathVariable Integer oscId,
            @Valid @RequestBody RequestUpdatePostDTO request,
            @AuthenticationPrincipal Usuario usuarioAuth) {
        return ResponseEntity.ok(postService.atualizarConteudoDaOsc(oscId, request, usuarioAuth));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/osc/{oscId}/put-com-imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostConteudoDTO> updateForOscWithImage(
            @PathVariable Integer oscId,
            @Valid @RequestPart("dados") RequestUpdatePostDTO request,
            @RequestPart(value = "imagem", required = false) MultipartFile imagem,
            @AuthenticationPrincipal Usuario usuarioAuth) {
        return ResponseEntity.ok(postService.atualizarConteudoDaOsc(oscId, request, usuarioAuth, imagem));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping(value = "/osc/{oscId}/delete/{postId}")
    public ResponseEntity<Void> deleteForOsc(
            @PathVariable Integer oscId,
            @PathVariable Integer postId,
            @AuthenticationPrincipal Usuario usuarioAuth) {
        postService.excluirConteudoDaOsc(oscId, postId, usuarioAuth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/osc/{oscId}/publicos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PostConteudoDTO>> listPublicForOsc(@PathVariable Integer oscId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().mustRevalidate())
                .body(postService.listarConteudoPublicoDaOsc(oscId));
    }

    @GetMapping(value = "/osc/{oscId}/publicos/{postId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PostConteudoDTO> getPublicForOsc(
            @PathVariable Integer oscId, @PathVariable Integer postId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().mustRevalidate())
                .body(postService.buscarConteudoPublicoDaOsc(oscId, postId));
    }

    @GetMapping("/causa-social")
    public ResponseEntity<List<ResponsePostDTO>> getPostsPorCausaSocial(
            @RequestParam CausaSocial causa) {

        return ResponseEntity.ok(
                postService.getPostsPorCausaSocial(causa)
        );
    }

    @GetMapping("/all")
    public ResponseEntity<Page<ResponsePostDTO>> getPosts(Pageable pageable) {
        return ResponseEntity.ok(postService.getAll(pageable));
    }

    @GetMapping(value = "/publicos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaginaVitrineDTO<PostConteudoDTO>> listarPublicacoesHome(Pageable pageable) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().mustRevalidate())
                .body(PaginaVitrineDTO.de(postService.listarPublicacoesHome(pageable)));
    }

    @GetMapping(value = "/voluntariados", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaginaVitrineDTO<PostConteudoDTO>> listarVoluntariadosHome(Pageable pageable) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().mustRevalidate())
                .body(PaginaVitrineDTO.de(postService.listarVoluntariadosHome(pageable)));
    }

    @GetMapping(value = "/catalogo/publicacoes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaginaVitrineDTO<PostConteudoDTO>> listarPublicacoesCatalogo(Pageable pageable) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().mustRevalidate())
                .body(PaginaVitrineDTO.de(postService.listarPublicacoesCatalogo(pageable)));
    }

    @GetMapping(value = "/catalogo/voluntariados", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaginaVitrineDTO<PostConteudoDTO>> listarVoluntariadosCatalogo(Pageable pageable) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().mustRevalidate())
                .body(PaginaVitrineDTO.de(postService.listarVoluntariadosCatalogo(pageable)));
    }
}
