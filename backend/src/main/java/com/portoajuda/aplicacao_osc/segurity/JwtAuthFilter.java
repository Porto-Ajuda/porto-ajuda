package com.portoajuda.aplicacao_osc.segurity;

import com.portoajuda.aplicacao_osc.entity.SessaoUsuario;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.service.SessaoUsuarioService;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String ACCESS_COOKIE = "PA_ACCESS";
    private static final Set<String> ROTAS_PUBLICAS_DE_AUTENTICACAO = Set.of(
            "/usuario/register",
            "/usuario/login",
            "/usuario/confirmar-email",
            "/usuario/trocar-codigo",
            "/usuario/reenviar-confirmacao",
            "/usuario/refresh");

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final SessaoUsuarioService sessaoUsuarioService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String caminho = request.getServletPath();
        String metodo = request.getMethod();

        return ("GET".equalsIgnoreCase(metodo)
                && ("/usuario/csrf".equals(caminho)
                        || "/usuario/confirmar-email".equals(caminho)))
                || ("POST".equalsIgnoreCase(metodo)
                        && ROTAS_PUBLICAS_DE_AUTENTICACAO.contains(caminho));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = obterToken(request);

        if (token != null
                && !token.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                autenticar(token, request);
            } catch (JwtException | NumberFormatException erroToken) {
                // JWT expirado, malformado ou com subject inválido é não autenticado.
                SecurityContextHolder.clearContext();
            }
        }

        // A cadeia roda uma vez e fora do bloco que trata erros de token.
        // Exceções de banco e da aplicação seguem para o tratamento HTTP 5xx.
        filterChain.doFilter(request, response);
    }

    private void autenticar(
            String token,
            HttpServletRequest request) {

        String subject = jwtService.extractSubject(token);
        Integer usuarioId = Integer.valueOf(subject);
        Long sessaoId = jwtService.extractSessionId(token);

        if (sessaoId == null) {
            return;
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElse(null);

        if (usuario == null || !usuario.isEnabled()) {
            return;
        }

        if (!jwtService.validateToken(token, usuario)) {
            return;
        }

        SessaoUsuario sessao = sessaoUsuarioService
                .buscarSessaoValida(sessaoId)
                .orElse(null);

        if (sessao == null
                || !sessao.getUsuario().getId().equals(usuario.getId())) {
            return;
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                usuario,
                null,
                usuario.getAuthorities());

        authToken.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request));

        SecurityContextHolder.getContext()
                .setAuthentication(authToken);
    }

    private String obterToken(HttpServletRequest request) {

        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // Compatibilidade temporária com clientes antigos.
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }
}
