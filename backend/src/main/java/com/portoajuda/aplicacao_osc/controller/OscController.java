package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.RequestNewMembroDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestOscDTO;
import com.portoajuda.aplicacao_osc.dto.request.UpdateOscDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.service.OscService;
import com.portoajuda.aplicacao_osc.utils.Email;
import io.jsonwebtoken.Jwts;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/osc")
public class OscController {
    private final OscService oscService;

    @PreAuthorize("hasRole('USUARIO')")
    @PostMapping(
            value = "/create",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> create(@Valid @RequestBody RequestOscDTO oscDTO, @AuthenticationPrincipal Usuario usuario){
        oscService.create(oscDTO, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PreAuthorize("hasRole('OSC')")
    @PutMapping(
            value = "/put",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> put(@Valid @RequestBody UpdateOscDTO updateOscDTO, @AuthenticationPrincipal Usuario usuario) throws AccessDeniedException {
        oscService.update(updateOscDTO, usuario);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping(
            value = "/list",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Page<Osc>> viewAll(Pageable pageable){
        Page<Osc> oscs = oscService.viewAll(pageable);
        return ResponseEntity.ok(oscs);
    }

    @PreAuthorize("hasRole('OSC')")
    @DeleteMapping(
            value = "/delete",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> delete(@Valid @AuthenticationPrincipal Usuario usuario) throws AccessDeniedException {
        oscService.delete(usuario);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PreAuthorize("hasRole('OSC')")
    @PostMapping(
            value = "/newMembro",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> addMembro(@Valid @RequestBody RequestNewMembroDTO newMembroDTO, @AuthenticationPrincipal Usuario usuario) throws AccessDeniedException {
        oscService.addMembro(usuario, newMembroDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PreAuthorize("hasRole('OSC')")
    @DeleteMapping(
            value = "/newMembro",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> removeMembro(@AuthenticationPrincipal Usuario usuario, Email email) throws AccessDeniedException {
        oscService.removeMembro(usuario, email);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}