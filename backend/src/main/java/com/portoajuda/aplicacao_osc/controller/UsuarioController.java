package com.portoajuda.aplicacao_osc.controller;

// import com.portoajuda.aplicacao_osc.dto.response.ResponseUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.service.CodigoAutenticacaoService;
import com.portoajuda.aplicacao_osc.service.CodigoAutenticacaoService.LoginPorCodigo;
import com.portoajuda.aplicacao_osc.service.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private static final String ACCESS_COOKIE = "PA_ACCESS";
    private static final String REFRESH_COOKIE = "PA_REFRESH";

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final CodigoAutenticacaoService codigoAutenticacaoService;
    private final UsuarioService usuarioService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.cookies.secure:true}")
    private boolean secureCookies;

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
            // Não revelar se o endereço está cadastrado.
        }

        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of(
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

            if (usuario == null
                    || !jwtService.validateEmailVerificationToken(token, usuario)) {
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
            @RequestBody Map<String, String> requisicao,
            HttpServletRequest request) {

        try {
            String codigo = requisicao.get("codigo");

            LoginPorCodigo resultado = codigoAutenticacaoService.trocarPorLogin(codigo);

            ResponseCookie accessCookie = criarCookie(
                    ACCESS_COOKIE,
                    resultado.accessToken(),
                    Duration.ofMinutes(15),
                    true);

            // Cookie de sessão: sem Max-Age/Expires.
            ResponseCookie refreshCookie = criarCookie(
                    REFRESH_COOKIE,
                    resultado.refreshToken(),
                    null,
                    true);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(resultado.usuario());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(Map.of(
                            "mensagem",
                            "Código inválido, expirado ou já utilizado."));
        }
    }

    private ResponseCookie criarCookie(
            String nome,
            String valor,
            Duration duracao,
            boolean httpOnly) {

        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(nome, valor)
                .httpOnly(httpOnly)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/");

        if (duracao != null) {
            builder.maxAge(duracao);
        }

        return builder.build();
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

    @GetMapping("/sessao-atual")
    public ResponseEntity<Map<String, Object>> verificarSessaoAtual(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("autenticado", false));
        }

        return ResponseEntity.ok(Map.of(
                "autenticado", true,
                "usuario", authentication.getName(),
                "autoridades", authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()));
    }

}