package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.RequestNewMembroDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestCargoMembroDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestOscDTO;
import com.portoajuda.aplicacao_osc.dto.request.UpdateOscDTO;

import com.portoajuda.aplicacao_osc.dto.response.ConsultaCnpjDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscListagemDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscPerfilDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscMembroDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscEdicaoDTO;

import com.portoajuda.aplicacao_osc.entity.Usuario;

import com.portoajuda.aplicacao_osc.service.OscService;
import com.portoajuda.aplicacao_osc.service.ValidacaoCnpjOscService;

import com.portoajuda.aplicacao_osc.utils.Email;

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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/osc")
public class OscController {

    private final OscService oscService;
    private final ValidacaoCnpjOscService validacaoCnpjOscService;

    // =====================================================
    // CADASTRO
    // =====================================================

    @PreAuthorize("hasRole('USUARIO')")
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseOscDTO> create(
            @Valid @RequestPart("dados") RequestOscDTO oscDTO,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @AuthenticationPrincipal Usuario usuario) {

        ResponseOscDTO criada = oscService.create(
                oscDTO,
                usuario,
                logo);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(criada);
    }

    @PreAuthorize("hasRole('USUARIO')")
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseOscDTO> createJson(
            @Valid @RequestBody RequestOscDTO oscDTO,
            @AuthenticationPrincipal Usuario usuario) {

        ResponseOscDTO criada = oscService.create(
                oscDTO,
                usuario,
                null);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(criada);
    }

    // =====================================================
    // ATUALIZAÇÃO
    // CRIADOR, ADMINISTRADOR E EDITOR
    // =====================================================

    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/put", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> put(
            @Valid @RequestBody UpdateOscDTO updateOscDTO,
            @AuthenticationPrincipal Usuario usuario) {

        oscService.update(updateOscDTO, usuario);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    // =====================================================
    // EXCLUSÃO
    // CRIADOR E ADMINISTRADOR
    // =====================================================

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Integer id,
            @AuthenticationPrincipal Usuario usuario) {

        oscService.delete(id, usuario);

        return ResponseEntity
                .noContent()
                .build();
    }

    // =====================================================
    // MEMBROS
    // CRIADOR E ADMINISTRADOR
    // =====================================================

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{id}/membros", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> addMembro(
            @PathVariable Integer id,
            @Valid @RequestBody RequestNewMembroDTO newMembroDTO,
            @AuthenticationPrincipal Usuario usuario) {

        oscService.addMembro(
                id,
                usuario,
                newMembroDTO);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}/membros")
    public ResponseEntity<Void> removeMembro(
            @PathVariable Integer id,
            @RequestParam Email email,
            @AuthenticationPrincipal Usuario usuario) {

        oscService.removeMembro(
                id,
                usuario,
                email);

        return ResponseEntity
                .noContent()
                .build();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/{id}/membros", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OscMembroDTO>> listarMembros(
            @PathVariable Integer id,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(oscService.listarMembros(id, usuario));
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping(value = "/{id}/membros/{usuarioId}/cargo", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> alterarCargoMembro(
            @PathVariable Integer id,
            @PathVariable Integer usuarioId,
            @Valid @RequestBody RequestCargoMembroDTO dados,
            @AuthenticationPrincipal Usuario usuario) {
        oscService.alterarCargoMembro(id, usuarioId, usuario, dados.cargo());
        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // CONSULTAS
    // =====================================================

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<OscListagemDTO>> viewAll(
            Pageable pageable) {

        Page<OscListagemDTO> oscs = oscService.viewAll(pageable);

        return ResponseEntity.ok(oscs);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/minhas", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OscUsuarioDTO>> listarMinhas(
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(
                oscService.listarDoUsuario(usuario));
    }

    @GetMapping(value = "/consultar-cnpj/{cnpj}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConsultaCnpjDTO> consultarCnpj(
            @PathVariable String cnpj) {

        ConsultaCnpjDTO resultado = validacaoCnpjOscService.validar(cnpj);

        return ResponseEntity.ok(resultado);
    }

    // Perfil público: não exige autenticação.
    @GetMapping("/{id}")
    public ResponseEntity<OscPerfilDTO> buscarPerfil(
            @PathVariable Integer id) {

        return ResponseEntity.ok(
                oscService.buscarPerfilPublico(id));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/{id}/edicao", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OscEdicaoDTO> buscarDadosEdicao(
            @PathVariable Integer id,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(oscService.buscarDadosEdicao(id, usuario));
    }
}
