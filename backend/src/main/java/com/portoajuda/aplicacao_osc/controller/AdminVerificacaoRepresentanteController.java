package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.dto.request.RequestRevisaoVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestReabrirVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.response.VerificacaoRepresentanteDetalheAdminDTO;
import com.portoajuda.aplicacao_osc.service.VerificacaoRepresentanteService;
import com.portoajuda.aplicacao_osc.service.VerificacaoRepresentanteService.QueuePage;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ngo-verifications")
@RequiredArgsConstructor
@Validated
public class AdminVerificacaoRepresentanteController {
    private final VerificacaoRepresentanteService verification;

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).NGO_VERIFICATION_READ)")
    @GetMapping
    public ResponseEntity<QueuePage> queue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(verification.queue(status, page, size));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).NGO_VERIFICATION_READ)")
    @GetMapping("/{applicationId}")
    public ResponseEntity<VerificacaoRepresentanteDetalheAdminDTO> detail(
            @PathVariable @Min(1) long applicationId,
            @AuthenticationPrincipal Usuario reviewer) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(verification.adminDetail(applicationId, reviewer));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).NGO_VERIFICATION_REVIEW)")
    @PostMapping("/{applicationId}/iniciar")
    public ResponseEntity<VerificacaoRepresentanteDetalheAdminDTO> startReview(
            @PathVariable @Min(1) long applicationId,
            @AuthenticationPrincipal Usuario reviewer) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(verification.startReview(applicationId, reviewer));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).NGO_VERIFICATION_REVIEW)")
    @PostMapping("/{applicationId}/decisao")
    public ResponseEntity<VerificacaoRepresentanteDetalheAdminDTO> decide(
            @PathVariable @Min(1) long applicationId,
            @Valid @RequestBody RequestRevisaoVerificacaoDTO request,
            @AuthenticationPrincipal Usuario reviewer) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(verification.decide(applicationId, request, reviewer));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).NGO_VERIFICATION_REVIEW)")
    @PostMapping("/{applicationId}/reabrir")
    public ResponseEntity<VerificacaoRepresentanteDetalheAdminDTO> reopen(
            @PathVariable @Min(1) long applicationId,
            @Valid @RequestBody RequestReabrirVerificacaoDTO request,
            @AuthenticationPrincipal Usuario reviewer) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(verification.reopenRejected(applicationId, request, reviewer));
    }
}
