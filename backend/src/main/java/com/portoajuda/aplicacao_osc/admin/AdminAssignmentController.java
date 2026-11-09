package com.portoajuda.aplicacao_osc.admin;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/assignments")
@RequiredArgsConstructor
public class AdminAssignmentController {

    private final AdminAssignmentService assignments;

    @GetMapping
    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).ADMIN_MANAGE)")
    public ResponseEntity<List<AdminAssignmentDTO>> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(assignments.list(limit, offset));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).ADMIN_MANAGE)")
    public ResponseEntity<AdminAssignmentDTO> assign(@Valid @RequestBody AdminAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(assignments.assignOrChange(request));
    }

    @PatchMapping("/{assignmentId}/profile")
    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).ADMIN_MANAGE)")
    public ResponseEntity<AdminAssignmentDTO> changeProfile(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody AdminAssignmentProfileRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(assignments.changeProfile(assignmentId, request));
    }

    @PostMapping("/{assignmentId}/revoke")
    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).ADMIN_MANAGE)")
    public ResponseEntity<AdminAssignmentDTO> revoke(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody AdminAssignmentRevokeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(assignments.revoke(assignmentId, request));
    }
}
