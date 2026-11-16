package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.response.VerificacaoRepresentanteDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestIniciarVerificacaoDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestReaplicarVerificacaoDTO;
import jakarta.validation.Valid;
import com.portoajuda.aplicacao_osc.dto.response.VerificacaoRepresentanteDetalheAdminDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.VerificacaoRepresentanteService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/osc")
@RequiredArgsConstructor
@Validated
public class VerificacaoRepresentanteController {
    private final VerificacaoRepresentanteService verification;

    @GetMapping("/verificacao/config")
    public ResponseEntity<Map<String, Boolean>> config() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("enabled", verification.isEnabled()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{oscId}/verificacao")
    public ResponseEntity<VerificacaoRepresentanteDTO> applicantApplication(
            @PathVariable @Min(1) int oscId,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(verification.applicantApplication(oscId, actor));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{oscId}/verificacao/solicitar")
    public ResponseEntity<VerificacaoRepresentanteDTO> startApplication(
            @PathVariable @Min(1) int oscId,
            @Valid @org.springframework.web.bind.annotation.RequestBody RequestIniciarVerificacaoDTO request,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(verification.startApplication(oscId, request, actor));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{oscId}/verificacao/reaplicar")
    public ResponseEntity<VerificacaoRepresentanteDTO> reapplyAfterRejection(
            @PathVariable @Min(1) int oscId,
            @Valid @org.springframework.web.bind.annotation.RequestBody RequestReaplicarVerificacaoDTO request,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(verification.reapplyAfterRejection(oscId, request, actor));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{oscId}/verificacao/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificacaoRepresentanteDTO> uploadEvidence(
            @PathVariable @Min(1) int oscId,
            @RequestParam("tipo") String documentType,
            @RequestPart("arquivo") MultipartFile file,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(verification.uploadEvidence(oscId, documentType, file, actor));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{oscId}/verificacao/enviar")
    public ResponseEntity<VerificacaoRepresentanteDTO> submitForReview(
            @PathVariable @Min(1) int oscId,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(verification.submitForReview(oscId, actor));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{oscId}/verificacao/documentos/{evidenceId}/acesso")
    public ResponseEntity<VerificacaoRepresentanteDetalheAdminDTO.EvidenciaAdminDTO> applicantEvidenceAccess(
            @PathVariable @Min(1) int oscId,
            @PathVariable @Min(1) long evidenceId,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(verification.applicantEvidenceSignedAccess(oscId, evidenceId, actor));
    }
}
