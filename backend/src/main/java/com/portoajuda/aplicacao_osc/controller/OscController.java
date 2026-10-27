package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.RequestNewMembroDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestOscDTO;
import com.portoajuda.aplicacao_osc.dto.request.UpdateOscDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
// import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.service.OscService;
import com.portoajuda.aplicacao_osc.utils.Email;
// import io.jsonwebtoken.Jwts;
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

import java.nio.file.AccessDeniedException;

import com.portoajuda.aplicacao_osc.dto.response.ConsultaCnpjDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscListagemDTO;
import com.portoajuda.aplicacao_osc.service.ValidacaoCnpjOscService;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
@RequestMapping("/osc")
public class OscController {
    private final OscService oscService;
    private final ValidacaoCnpjOscService validacaoCnpjOscService;

    @PreAuthorize("hasRole('USUARIO')")
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseOscDTO> create(
            @Valid @RequestPart("dados") RequestOscDTO oscDTO,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @AuthenticationPrincipal Usuario usuario) {
        ResponseOscDTO criada = oscService.create(oscDTO, usuario, logo);
        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    @PreAuthorize("hasRole('USUARIO')")
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseOscDTO> createJson(
            @Valid @RequestBody RequestOscDTO oscDTO,
            @AuthenticationPrincipal Usuario usuario) {
        ResponseOscDTO criada = oscService.create(oscDTO, usuario, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    @PreAuthorize("hasRole('OSC')")
    @PutMapping(value = "/put", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> put(@Valid @RequestBody UpdateOscDTO updateOscDTO,
            @AuthenticationPrincipal Usuario usuario) throws AccessDeniedException {
        oscService.update(updateOscDTO, usuario);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<OscListagemDTO>> viewAll(Pageable pageable) {
        Page<OscListagemDTO> oscs = oscService.viewAll(pageable);
        return ResponseEntity.ok(oscs);
    }

    @GetMapping(value = "/consultar-cnpj/{cnpj}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConsultaCnpjDTO> consultarCnpj(
            @PathVariable String cnpj) {

        ConsultaCnpjDTO resultado = validacaoCnpjOscService.validar(cnpj);

        return ResponseEntity.ok(resultado);
    }

    @PreAuthorize("hasRole('OSC')")
    @DeleteMapping(value = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> delete(@Valid @AuthenticationPrincipal Usuario usuario) throws AccessDeniedException {
        oscService.delete(usuario);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PreAuthorize("hasRole('OSC')")
    @PostMapping(value = "/newMembro", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> addMembro(@Valid @RequestBody RequestNewMembroDTO newMembroDTO,
            @AuthenticationPrincipal Usuario usuario) throws AccessDeniedException {
        oscService.addMembro(usuario, newMembroDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PreAuthorize("hasRole('OSC')")
    @DeleteMapping(value = "/removeMembro", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> removeMembro(@AuthenticationPrincipal Usuario usuario, Email email)
            throws AccessDeniedException {
        oscService.removeMembro(usuario, email);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
