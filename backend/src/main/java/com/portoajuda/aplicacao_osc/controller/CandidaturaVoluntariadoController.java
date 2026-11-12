package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.RequestCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestDecisaoCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.response.CandidaturaOngDTO;
import com.portoajuda.aplicacao_osc.dto.response.CandidaturaUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.DisponibilidadeCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.response.SituacaoMinhaCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.response.PaginaVitrineDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import com.portoajuda.aplicacao_osc.service.CandidaturaVoluntariadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CandidaturaVoluntariadoController {

    private final CandidaturaVoluntariadoService candidaturaService;

    @PreAuthorize("permitAll()")
    @GetMapping("/voluntariado/vagas/{vagaId}/disponibilidade")
    public ResponseEntity<DisponibilidadeCandidaturaDTO> disponibilidade(@PathVariable Integer vagaId) {
        return ResponseEntity.ok(candidaturaService.disponibilidadePublica(vagaId));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/voluntariado/vagas/{vagaId}/minha-candidatura")
    public ResponseEntity<SituacaoMinhaCandidaturaDTO> situacaoMinhaCandidatura(
            @PathVariable Integer vagaId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(candidaturaService.situacaoMinhaCandidatura(vagaId, usuario));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/voluntariado/vagas/{vagaId}/candidaturas")
    public ResponseEntity<CandidaturaUsuarioDTO> candidatar(
            @PathVariable Integer vagaId,
            @Valid @RequestBody(required = false) RequestCandidaturaDTO request,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(candidaturaService.candidatar(vagaId, request, usuario));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/usuario/candidaturas")
    public ResponseEntity<PaginaVitrineDTO<CandidaturaUsuarioDTO>> listarMinhas(
            Pageable pageable,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(PaginaVitrineDTO.de(candidaturaService.listarMinhas(usuario, pageable)));
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/usuario/candidaturas/{candidaturaId}/cancelar")
    public ResponseEntity<CandidaturaUsuarioDTO> cancelarMinha(
            @PathVariable Integer candidaturaId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(candidaturaService.cancelarMinha(candidaturaId, usuario));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/osc/{oscId}/candidaturas")
    public ResponseEntity<PaginaVitrineDTO<CandidaturaOngDTO>> listarDaOsc(
            @PathVariable Integer oscId,
            @RequestParam(required = false) StatusCandidatura status,
            Pageable pageable,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(PaginaVitrineDTO.de(
                candidaturaService.listarDaOsc(oscId, status, usuario, pageable)));
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/osc/{oscId}/candidaturas/{candidaturaId}")
    public ResponseEntity<CandidaturaOngDTO> decidir(
            @PathVariable Integer oscId,
            @PathVariable Integer candidaturaId,
            @Valid @RequestBody RequestDecisaoCandidaturaDTO request,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(candidaturaService.decidir(oscId, candidaturaId, request, usuario));
    }
}
