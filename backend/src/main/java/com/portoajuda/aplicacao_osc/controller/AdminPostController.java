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
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only aliases keep the privileged cookie scoped to /admin. */
@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).CONTENT_PUBLISH)")
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> create(
            @Valid @RequestBody RequestCreatePostDTO request,
            @RequestHeader(value = "X-Admin-Reason", required = false) String reason,
            @AuthenticationPrincipal Usuario administrator) {
        postService.create(request, administrator, reason);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).CONTENT_EDIT_NEWS)")
    @PutMapping(value = "/put", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(
            @Valid @RequestBody RequestUpdatePostDTO request,
            @RequestHeader(value = "X-Admin-Reason", required = false) String reason,
            @AuthenticationPrincipal Usuario administrator) {
        postService.update(request, administrator, reason);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).CONTENT_DELETE)")
    @DeleteMapping(value = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> delete(
            @Valid @RequestBody Integer postId,
            @RequestHeader(value = "X-Admin-Reason", required = false) String reason,
            @AuthenticationPrincipal Usuario administrator) {
        postService.delete(postId, administrator, reason);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).CONTENT_READ)")
    @GetMapping("/meus-posts")
    public ResponseEntity<List<ResponsePostDTO>> myPosts(
            @AuthenticationPrincipal Usuario administrator) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(postService.getMeusPosts(administrator));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).CONTENT_READ)")
    @GetMapping("/all")
    public ResponseEntity<Page<ResponsePostDTO>> all(Pageable pageable) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(postService.getAll(pageable));
    }
}
