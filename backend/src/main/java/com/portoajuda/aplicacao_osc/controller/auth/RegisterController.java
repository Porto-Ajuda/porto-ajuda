package com.portoajuda.aplicacao_osc.controller.auth;

import com.portoajuda.aplicacao_osc.dto.request.RequestUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseUsuarioDTO;
import com.portoajuda.aplicacao_osc.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class RegisterController {
    private final UsuarioService usuarioService;

    @PostMapping(
            value = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResponseUsuarioDTO> register(@Valid @RequestBody RequestUsuarioDTO usuarioDTO){
        ResponseUsuarioDTO response = usuarioService.cadastro(usuarioDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}