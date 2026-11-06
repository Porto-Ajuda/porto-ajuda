package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.RequestAtualizarStatusOscDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.OscStatusService;
import com.portoajuda.aplicacao_osc.service.OscService;
import com.portoajuda.aplicacao_osc.dto.response.OscPerfilDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/admin/oscs")
@RequiredArgsConstructor
public class AdminOscController {

    private final OscStatusService oscStatusService;
    private final OscService oscService;

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_SESSION')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> atualizarStatus(
            @PathVariable Integer id,
            @Valid @RequestBody RequestAtualizarStatusOscDTO request,
            @AuthenticationPrincipal Usuario administrador) {
        oscStatusService.atualizarPelaPlataforma(id, request, administrador);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_SESSION')")
    @GetMapping("/{id}/perfil-privado")
    public ResponseEntity<OscPerfilDTO> obterPerfilPrivado(@PathVariable Integer id,
            @AuthenticationPrincipal Usuario administrador) {
        return ResponseEntity.ok(oscService.buscarPerfilPrivado(id, administrador));
    }
}
