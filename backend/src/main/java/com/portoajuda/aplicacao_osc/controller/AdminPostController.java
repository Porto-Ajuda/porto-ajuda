package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.RequestCreatePostDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestUpdatePostDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponsePostDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.PostService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only aliases keep the privileged cookie scoped to /admin. */
@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_SESSION')")
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> create(
            @Valid @RequestBody RequestCreatePostDTO request,
            @AuthenticationPrincipal Usuario administrator) {
        postService.create(request, administrator);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_SESSION')")
    @PutMapping(value = "/put", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(
            @Valid @RequestBody RequestUpdatePostDTO request,
            @AuthenticationPrincipal Usuario administrator) {
        postService.update(request, administrator);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_SESSION')")
    @DeleteMapping(value = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> delete(
            @Valid @RequestBody Integer postId,
            @AuthenticationPrincipal Usuario administrator) {
        postService.delete(postId, administrator);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_SESSION')")
    @GetMapping("/meus-posts")
    public ResponseEntity<List<ResponsePostDTO>> myPosts(
            @AuthenticationPrincipal Usuario administrator) {
        return ResponseEntity.ok(postService.getMeusPosts(administrator));
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_SESSION')")
    @GetMapping("/all")
    public ResponseEntity<Page<ResponsePostDTO>> all(Pageable pageable) {
        return ResponseEntity.ok(postService.getAll(pageable));
    }
}
