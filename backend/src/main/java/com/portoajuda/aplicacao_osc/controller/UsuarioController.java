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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
// import com.portoajuda.aplicacao_osc.dto.response.ResponsePerfilUsuarioDTO;

import com.portoajuda.aplicacao_osc.dto.request.RequestAtualizarPerfilDTO;
import jakarta.validation.Valid;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Set;

import com.portoajuda.aplicacao_osc.dto.request.RequestPreferenciasAcessibilidadeDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponsePreferenciasAcessibilidadeDTO;
import com.portoajuda.aplicacao_osc.entity.PreferenciasAcessibilidade;
import com.portoajuda.aplicacao_osc.repository.PreferenciasAcessibilidadeRepository;

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
        private final PreferenciasAcessibilidadeRepository preferenciasAcessibilidadeRepository;

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

        @GetMapping("/perfil")
        @Transactional(readOnly = true)
        public ResponseEntity<?> obterPerfil(Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()
                                || authentication instanceof AnonymousAuthenticationToken) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", "Autenticação necessária."));
                }

                try {
                        Integer usuarioId = Integer.valueOf(authentication.getName());

                        Usuario usuario = usuarioRepository.findById(usuarioId)
                                        .orElse(null);

                        if (usuario == null || !usuario.isEnabled()) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                .body(Map.of("mensagem", "Usuário não encontrado ou inativo."));
                        }

                        var perfil = new com.portoajuda.aplicacao_osc.dto.response.ResponsePerfilUsuarioDTO(
                                        usuario.getId(),
                                        usuario.getNome(),
                                        usuario.getNomeSocial(),
                                        usuario.getEmail() == null
                                                        ? null
                                                        : usuario.getEmail().valor(),
                                        usuario.getTelefone(),
                                        usuario.getCep(),
                                        usuario.getDataNascimento(),
                                        usuario.getDataCriacao(),
                                        usuario.getFotoPerfilUrl(),
                                        usuario.getGenero() == null
                                                        ? null
                                                        : usuario.getGenero().name());

                        return ResponseEntity.ok()
                                        .cacheControl(org.springframework.http.CacheControl.noStore())
                                        .body(perfil);

                } catch (NumberFormatException e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", "Sessão inválida."));
                }
        }

        @PatchMapping("/perfil")
        @Transactional
        public ResponseEntity<?> atualizarPerfil(
                        @Valid @RequestBody RequestAtualizarPerfilDTO dados,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()
                                || authentication instanceof AnonymousAuthenticationToken) {

                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", "Autenticação necessária."));
                }

                try {
                        Integer usuarioId = Integer.valueOf(authentication.getName());

                        Usuario usuario = usuarioService.atualizarPerfil(
                                        usuarioId,
                                        dados);

                        var perfil = new com.portoajuda.aplicacao_osc.dto.response.ResponsePerfilUsuarioDTO(
                                        usuario.getId(),
                                        usuario.getNome(),
                                        usuario.getNomeSocial(),
                                        usuario.getEmail() == null
                                                        ? null
                                                        : usuario.getEmail().valor(),
                                        usuario.getTelefone(),
                                        usuario.getCep(),
                                        usuario.getDataNascimento(),
                                        usuario.getDataCriacao(),
                                        usuario.getFotoPerfilUrl(),
                                        usuario.getGenero() == null
                                                        ? null
                                                        : usuario.getGenero().name());

                        return ResponseEntity.ok()
                                        .cacheControl(
                                                        org.springframework.http.CacheControl.noStore())
                                        .body(perfil);

                } catch (NumberFormatException e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", "Sessão inválida."));

                } catch (IllegalArgumentException | IllegalStateException e) {
                        return ResponseEntity.badRequest()
                                        .body(Map.of("mensagem", e.getMessage()));
                }
        }

        @PostMapping(value = "/foto-perfil", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<?> atualizarFotoPerfil(
                        @RequestParam("foto") MultipartFile foto,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()
                                || authentication instanceof AnonymousAuthenticationToken) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", "Autenticação necessária."));
                }

                if (foto == null || foto.isEmpty()) {
                        return ResponseEntity.badRequest()
                                        .body(Map.of("mensagem", "Selecione uma imagem."));
                }

                if (foto.getSize() > 10L * 1024 * 1024) {
                        return ResponseEntity.badRequest()
                                        .body(Map.of("mensagem", "A imagem não pode ultrapassar 10 MB."));
                }

                String contentType = foto.getContentType();

                if (contentType == null
                                || !Set.of("image/jpeg", "image/png", "image/webp")
                                                .contains(contentType.toLowerCase(java.util.Locale.ROOT))) {
                        return ResponseEntity.badRequest()
                                        .body(Map.of("mensagem", "Formato permitido: JPEG, PNG ou WebP."));
                }

                try {
                        byte[] arquivo = foto.getBytes();

                        if (!assinaturaImagemValida(arquivo, contentType)) {
                                return ResponseEntity.badRequest()
                                                .body(Map.of("mensagem",
                                                                "O conteúdo não corresponde a uma imagem válida."));
                        }

                        Integer usuarioId = Integer.valueOf(authentication.getName());

                        String url = usuarioService.atualizarFotoPerfil(
                                        usuarioId,
                                        arquivo,
                                        contentType.toLowerCase(java.util.Locale.ROOT));

                        return ResponseEntity.ok()
                                        .cacheControl(org.springframework.http.CacheControl.noStore())
                                        .body(Map.of(
                                                        "mensagem", "Foto de perfil atualizada.",
                                                        "fotoPerfilUrl", url));

                } catch (NumberFormatException e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", "Sessão inválida."));

                } catch (IOException e) {
                        return ResponseEntity.badRequest()
                                        .body(Map.of("mensagem", "Não foi possível ler a imagem."));

                } catch (IllegalArgumentException | IllegalStateException e) {
                        return ResponseEntity.badRequest()
                                        .body(Map.of("mensagem", e.getMessage()));

                } catch (org.springframework.web.client.RestClientException e) {
                        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                        .body(Map.of("mensagem", "Não foi possível enviar a imagem ao armazenamento."));
                }
        }

        private boolean assinaturaImagemValida(byte[] dados, String contentType) {
                if (dados == null) {
                        return false;
                }

                return switch (contentType.toLowerCase(java.util.Locale.ROOT)) {
                        case "image/jpeg" ->
                                dados.length >= 3
                                                && (dados[0] & 0xFF) == 0xFF
                                                && (dados[1] & 0xFF) == 0xD8
                                                && (dados[2] & 0xFF) == 0xFF;

                        case "image/png" ->
                                dados.length >= 8
                                                && (dados[0] & 0xFF) == 0x89
                                                && dados[1] == 0x50
                                                && dados[2] == 0x4E
                                                && dados[3] == 0x47
                                                && dados[4] == 0x0D
                                                && dados[5] == 0x0A
                                                && dados[6] == 0x1A
                                                && dados[7] == 0x0A;

                        case "image/webp" ->
                                dados.length >= 12
                                                && dados[0] == 'R'
                                                && dados[1] == 'I'
                                                && dados[2] == 'F'
                                                && dados[3] == 'F'
                                                && dados[8] == 'W'
                                                && dados[9] == 'E'
                                                && dados[10] == 'B'
                                                && dados[11] == 'P';

                        default -> false;
                };
        }

        /*
         * =========================================================
         * PREFERÊNCIAS DE ACESSIBILIDADE
         * =========================================================
         */

        @GetMapping("/preferencias")
        @Transactional(readOnly = true)
        public ResponseEntity<?> obterPreferenciasAcessibilidade(
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()
                                || authentication instanceof AnonymousAuthenticationToken) {

                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", "Autenticação necessária."));
                }

                try {
                        Integer usuarioId = Integer.valueOf(authentication.getName());

                        Usuario usuario = usuarioRepository.findById(usuarioId)
                                        .orElse(null);

                        if (usuario == null || !usuario.isEnabled()) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                .body(Map.of("mensagem", "Usuário não encontrado ou inativo."));
                        }

                        return preferenciasAcessibilidadeRepository
                                        .findByUsuario_Id(usuarioId)
                                        .map(preferencias -> ResponseEntity.ok()
                                                        .cacheControl(
                                                                        org.springframework.http.CacheControl.noStore())
                                                        .body((Object) ResponsePreferenciasAcessibilidadeDTO
                                                                        .de(preferencias)))
                                        .orElseGet(() -> ResponseEntity.ok()
                                                        .cacheControl(
                                                                        org.springframework.http.CacheControl.noStore())
                                                        .body((Object) new ResponsePreferenciasAcessibilidadeDTO(
                                                                        100,
                                                                        "escuro",
                                                                        false,
                                                                        false)));

                } catch (NumberFormatException e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", "Sessão inválida."));
                }
        }

        @PutMapping("/preferencias")
        @Transactional
        public ResponseEntity<?> salvarPreferenciasAcessibilidade(
                        @Valid @RequestBody RequestPreferenciasAcessibilidadeDTO dados,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()
                                || authentication instanceof AnonymousAuthenticationToken) {

                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", "Autenticação necessária."));
                }

                try {
                        Integer usuarioId = Integer.valueOf(authentication.getName());

                        Usuario usuario = usuarioRepository.findById(usuarioId)
                                        .orElse(null);

                        if (usuario == null || !usuario.isEnabled()) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                .body(Map.of("mensagem", "Usuário não encontrado ou inativo."));
                        }

                        PreferenciasAcessibilidade preferencias = preferenciasAcessibilidadeRepository
                                        .findByUsuario_Id(usuarioId)
                                        .orElseGet(() -> {
                                                PreferenciasAcessibilidade nova = new PreferenciasAcessibilidade();
                                                nova.setUsuario(usuario);
                                                return nova;
                                        });

                        preferencias.setTamanhoFonte(dados.tamanhoFonte());
                        preferencias.setTema(dados.tema());
                        preferencias.setAltoContraste(dados.altoContraste());
                        preferencias.setLibras(dados.libras());

                        PreferenciasAcessibilidade salvas = preferenciasAcessibilidadeRepository.save(preferencias);

                        return ResponseEntity.ok()
                                        .cacheControl(
                                                        org.springframework.http.CacheControl.noStore())
                                        .body(ResponsePreferenciasAcessibilidadeDTO.de(salvas));

                } catch (NumberFormatException e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", "Sessão inválida."));
                }
        }

}