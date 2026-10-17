package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.response.ResponseLoginDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.service.CodigoAutenticacaoService;
import com.portoajuda.aplicacao_osc.service.UsuarioService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final CodigoAutenticacaoService codigoAutenticacaoService;
    private final UsuarioService usuarioService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/reenviar-confirmacao")
    public ResponseEntity<Map<String, String>> reenviarConfirmacao(
            @RequestBody Map<String, String> requisicao) {

        String email = requisicao.get("email");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "mensagem",
                            "Informe um endereço de e-mail válido."));
        }

        try {
            usuarioService.reenviarConfirmacaoEmail(email.trim());

        } catch (IllegalArgumentException | IllegalStateException e) {
            // Mantém a mesma resposta para não revelar
            // se determinado endereço está cadastrado.
        }

        return ResponseEntity.ok(Map.of(
                "mensagem",
                "Se houver uma conta pendente de confirmação, as instruções serão enviadas."));
    }

    @GetMapping("/confirmar-email")
    @Transactional
    public ResponseEntity<Void> confirmarEmail(
            @RequestParam("token") String token) {

        try {
            String subject = jwtService.extractSubject(token);
            Integer usuarioId = Integer.valueOf(subject);

            Usuario usuario = usuarioRepository.findById(usuarioId)
                    .orElse(null);

            if (usuario == null) {
                return redirecionar("erro", null);
            }

            if (!jwtService.validateEmailVerificationToken(token, usuario)) {
                return redirecionar("erro", null);
            }

            usuario.setEmailVerificado(true);
            usuarioRepository.save(usuario);

            String codigo = codigoAutenticacaoService.criarCodigo(usuario);

            return redirecionar("sucesso", codigo);

        } catch (Exception e) {
            // Em produção, registre a exceção com um logger.
            return redirecionar("erro", null);
        }
    }

    @PostMapping("/trocar-codigo")
    public ResponseEntity<?> trocarCodigo(
            @RequestBody Map<String, String> requisicao) {

        try {
            String codigo = requisicao.get("codigo");

            ResponseLoginDTO resposta = codigoAutenticacaoService.trocarPorLogin(codigo);

            return ResponseEntity.ok(resposta);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "mensagem",
                            "Código inválido, expirado ou já utilizado."));
        }
    }

    private ResponseEntity<Void> redirecionar(
            String status,
            String codigo) {

        String baseUrl = frontendUrl.replaceAll("/+$", "");

        String destino = baseUrl
                + "/pages/confirmacao-email.html?status="
                + UriUtils.encodeQueryParam(
                        status,
                        StandardCharsets.UTF_8);

        if (codigo != null) {
            destino += "#codigo="
                    + UriUtils.encodeFragment(
                            codigo,
                            StandardCharsets.UTF_8);
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(
                        HttpHeaders.LOCATION,
                        URI.create(destino).toString())
                .build();
    }
}