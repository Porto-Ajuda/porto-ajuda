package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.RequestLoginDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestCancelarExclusaoDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService.SessaoCriada;
import com.portoajuda.aplicacao_osc.service.UsuarioService;
import com.portoajuda.aplicacao_osc.service.UsuarioService.LoginResultado;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/usuario")
public class AuthController {

        private static final String ACCESS_COOKIE = "PA_ACCESS";
        private static final String REFRESH_COOKIE = "PA_REFRESH";

        private final UsuarioService usuarioService;
        private final SessaoUsuarioService sessaoUsuarioService;
        private final JwtService jwtService;

        @Value("${app.cookies.secure:true}")
        private boolean secureCookies;

        /**
         * Disponibiliza o token CSRF para o frontend.
         */
        @GetMapping(value = "/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<CsrfResposta> csrf(CsrfToken csrfToken) {
                return ResponseEntity.ok()
                                .cacheControl(CacheControl.noStore())
                                .body(new CsrfResposta(
                                                csrfToken.getHeaderName(),
                                                csrfToken.getToken()));
        }

        @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<Void> register(
                        @Valid @RequestBody RequestUsuarioDTO usuarioDTO) {

                usuarioService.signup(usuarioDTO);

                return ResponseEntity.status(HttpStatus.CREATED).build();
        }

        /**
         * Autentica o usuário e cria os cookies conforme lembrarDeMim.
         */
        @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<ResponseUsuarioDTO> login(
                        @Valid @RequestBody RequestLoginDTO loginDTO) {

                LoginResultado resultado = usuarioService.login(loginDTO);

                ResponseCookie accessCookie = criarCookie(
                                ACCESS_COOKIE,
                                resultado.accessToken(),
                                resultado.lembrarDeMim()
                                                ? Duration.ofMinutes(15)
                                                : null);

                Duration duracaoRefresh = resultado.lembrarDeMim()
                                ? duracaoRestante(resultado.expiraEm())
                                : null;

                ResponseCookie refreshCookie = criarCookie(
                                REFRESH_COOKIE,
                                resultado.refreshToken(),
                                duracaoRefresh);

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                                .cacheControl(CacheControl.noStore())
                                .body(resultado.usuario());
        }

        /**
         * Renova o access token e rotaciona o refresh token.
         */
        @PostMapping("/refresh")
        public ResponseEntity<?> refresh(HttpServletRequest request) {

                String refreshToken = obterCookie(request, REFRESH_COOKIE);

                Optional<SessaoCriada> resultado = sessaoUsuarioService.renovarSessao(refreshToken);

                if (resultado.isEmpty()) {
                        return limparCookies(HttpStatus.UNAUTHORIZED)
                                        .body(new RefreshErro("SESSAO_INVALIDA"));
                }

                SessaoCriada criada = resultado.get();
                SessaoUsuario sessao = criada.sessao();

                String novoAccessToken = jwtService.generateToken(
                                sessao.getUsuario(),
                                sessao.getId());

                ResponseCookie accessCookie = criarCookie(
                                ACCESS_COOKIE,
                                novoAccessToken,
                                sessao.isLembrarDeMim()
                                                ? Duration.ofMinutes(15)
                                                : null);

                Duration duracaoRefresh = sessao.isLembrarDeMim()
                                ? duracaoRestante(sessao.getExpiraEm())
                                : null;

                ResponseCookie refreshCookie = criarCookie(
                                REFRESH_COOKIE,
                                criada.refreshToken(),
                                duracaoRefresh);

                return ResponseEntity.noContent()
                                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                                .cacheControl(CacheControl.noStore())
                                .build();
        }

        /**
         * Revoga a sessão autenticada e limpa os cookies.
         */
        @PreAuthorize("hasRole('USUARIO')")
        @PostMapping("/logout")
        public ResponseEntity<Void> logout(
                        @AuthenticationPrincipal Usuario usuario,
                        HttpServletRequest request) {

                String accessToken = obterCookie(request, ACCESS_COOKIE);

                if (usuario != null
                                && accessToken != null
                                && !accessToken.isBlank()) {

                        Long sessaoId = jwtService.extractSessionId(accessToken);

                        if (sessaoId != null) {
                                Optional<SessaoUsuario> sessao = sessaoUsuarioService.buscarSessaoValida(sessaoId);

                                if (sessao.isPresent()
                                                && sessao.get().getUsuario().getId()
                                                                .equals(usuario.getId())) {

                                        sessaoUsuarioService.revogarSessao(sessaoId);
                                }
                        }
                }

                return limparCookies(HttpStatus.NO_CONTENT).build();
        }

        @PreAuthorize("hasRole('USUARIO')")
        @DeleteMapping(value = "/delete", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<?> delete(
                        @AuthenticationPrincipal Usuario usuario) {

                java.time.LocalDateTime exclusaoAgendada;
                try {
                        exclusaoAgendada = usuarioService.solicitarExclusao(usuario.getId());
                } catch (IllegalStateException e) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                        .cacheControl(CacheControl.noStore())
                                        .body(java.util.Map.of("mensagem", e.getMessage()));
                }

                return limparCookies(HttpStatus.ACCEPTED)
                                .body(java.util.Map.of(
                                                "dataExclusaoAgendada",
                                                exclusaoAgendada.toString()));
        }

        @PostMapping(value = "/cancelar-exclusao", consumes = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<Void> cancelarExclusao(
                        @Valid @RequestBody RequestCancelarExclusaoDTO requisicao) {

                if (!usuarioService.cancelarExclusao(
                                requisicao.email().trim(),
                                requisicao.senha())) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .cacheControl(CacheControl.noStore())
                                        .build();
                }

                return ResponseEntity.noContent()
                                .cacheControl(CacheControl.noStore())
                                .build();
        }

        @PreAuthorize("hasRole('USUARIO')")
        @PutMapping(value = "/put", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<Void> put(
                        @Valid @RequestBody RequestUsuarioDTO requestUsuarioDTO,
                        @AuthenticationPrincipal Usuario usuario) {

                usuarioService.update(requestUsuarioDTO, usuario);

                return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }

        @PreAuthorize("hasRole('USUARIO')")
        @GetMapping(value = "/get", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<ResponseUsuarioDTO> get(
                        @AuthenticationPrincipal Usuario usuario) {

                return ResponseEntity.ok(usuarioService.view(usuario));
        }

        /**
         * Cria um cookie.
         * duracao == null: cookie de sessão do navegador.
         * duracao > 0: cookie persistente pelo período informado.
         * duracao == 0: remove o cookie.
         */
        private ResponseCookie criarCookie(
                        String nome,
                        String valor,
                        Duration duracao) {

                ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(nome, valor)
                                .httpOnly(true)
                                .secure(secureCookies)
                                .sameSite("Lax")
                                .path("/");

                if (duracao != null) {
                        builder.maxAge(duracao.isNegative()
                                        ? Duration.ZERO
                                        : duracao);
                }

                return builder.build();
        }

        /**
         * Calcula o tempo restante da sessão persistente.
         */
        private Duration duracaoRestante(Instant expiraEm) {
                Duration restante = Duration.between(Instant.now(), expiraEm);

                return restante.isNegative() || restante.isZero()
                                ? Duration.ZERO
                                : restante;
        }

        private String obterCookie(
                        HttpServletRequest request,
                        String nome) {

                Cookie[] cookies = request.getCookies();

                if (cookies != null) {
                        for (Cookie cookie : cookies) {
                                if (nome.equals(cookie.getName())) {
                                        return cookie.getValue();
                                }
                        }
                }

                return null;
        }

        /**
         * Limpa os cookies no navegador.
         */
        private ResponseEntity.BodyBuilder limparCookies(HttpStatus status) {

                ResponseCookie accessCookie = criarCookie(
                                ACCESS_COOKIE,
                                "",
                                Duration.ZERO);

                ResponseCookie refreshCookie = criarCookie(
                                REFRESH_COOKIE,
                                "",
                                Duration.ZERO);

                return ResponseEntity.status(status)
                                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                                .cacheControl(CacheControl.noStore());
        }

        public record CsrfResposta(
                        String headerName,
                        String token) {
        }

        public record RefreshErro(String codigo) {
        }
}
