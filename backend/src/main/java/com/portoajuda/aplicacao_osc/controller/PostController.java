package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.RequestCreatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestUpdatePostDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponsePostDTO;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/post")
public class PostController {
    private final PostService postService;

    @PreAuthorize("hasAnyRole('OSC', 'ADMIN')")
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

    @PreAuthorize("hasAnyRole('OSC', 'ADMIN')")
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

    @PreAuthorize("hasAnyRole('OSC', 'ADMIN')")
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
}