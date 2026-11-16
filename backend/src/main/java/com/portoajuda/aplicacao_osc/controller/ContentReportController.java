package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.RequestContentReportDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestContentReportDecisionDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestContentBlockRestoreDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestPreventiveContentHideDTO;
import com.portoajuda.aplicacao_osc.dto.response.AdminContentReportDetailDTO;
import com.portoajuda.aplicacao_osc.dto.response.AdminContentReportQueueDTO;
import com.portoajuda.aplicacao_osc.dto.response.ContentReportDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.ContentReportService;
import com.portoajuda.aplicacao_osc.segurity.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ContentReportController {
    private final ContentReportService reports;
    private final ClientIpResolver clientIpResolver;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/denuncias")
    public ResponseEntity<ContentReportDTO> reportar(@Valid @RequestBody RequestContentReportDTO request,
            @AuthenticationPrincipal Usuario usuario, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reports.reportar(request, usuario, clientIpResolver.resolve(servletRequest)));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).REPORT_READ)")
    @GetMapping("/admin/denuncias")
    public ResponseEntity<AdminContentReportQueueDTO> fila(@RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "PENDING") String estado,
            @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "25") int limit) {
        List<com.portoajuda.aplicacao_osc.dto.response.AdminContentReportQueueItemDTO> items = reports.queue(tipo, estado, limit, offset);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AdminContentReportQueueDTO(
                Math.min(Math.max(limit, 1), 50), Math.max(offset, 0), reports.countAlvos(tipo, estado),
                items));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).REPORT_READ)")
    @GetMapping("/admin/denuncias/{tipo}/{alvoId}")
    public ResponseEntity<AdminContentReportDetailDTO> detalhe(@PathVariable String tipo, @PathVariable Integer alvoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.detail(tipo, alvoId));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).REPORT_REVIEW)")
    @PostMapping("/admin/denuncias/{tipo}/{alvoId}/decisao")
    public ResponseEntity<Void> decidir(@PathVariable String tipo, @PathVariable Integer alvoId,
            @Valid @RequestBody RequestContentReportDecisionDTO request) {
        reports.decidir(tipo, alvoId, request);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).REPORT_REVIEW)")
    @PostMapping("/admin/denuncias/{tipo}/{alvoId}/ocultacao-preventiva")
    public ResponseEntity<Void> ocultarEnquantoPendente(@PathVariable String tipo, @PathVariable Integer alvoId,
            @Valid @RequestBody RequestPreventiveContentHideDTO request) {
        reports.ocultarEnquantoPendente(tipo, alvoId, request.justificativa());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).CONTENT_REACTIVATE)")
    @PostMapping("/admin/denuncias/{tipo}/{alvoId}/bloqueios/{blockId}/restauracao")
    public ResponseEntity<Void> restaurarRestricao(@PathVariable String tipo, @PathVariable Integer alvoId,
            @PathVariable Long blockId, @Valid @RequestBody RequestContentBlockRestoreDTO request) {
        reports.restaurarRestricao(tipo, alvoId, blockId, request.justificativa());
        return ResponseEntity.noContent().build();
    }
}
