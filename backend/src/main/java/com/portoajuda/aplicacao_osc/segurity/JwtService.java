
package com.portoajuda.aplicacao_osc.segurity;

import com.portoajuda.aplicacao_osc.entity.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtService {

    private final String SECRET;

    // Token de acesso: 15 minutos.
    private static final Duration TOKEN_EXPIRATION =
            Duration.ofMinutes(15);

    // Token de confirmação de e-mail: 15 minutos.
    private static final Duration EMAIL_TOKEN_EXPIRATION =
            Duration.ofMinutes(15);

    private static final String ACCESS = "ACCESS";
    private static final String EMAIL_VERIFICATION =
            "EMAIL_VERIFICATION";

    public JwtService(
            @Value("${JWT_SECRET_KEY}") String secret
    ) {
        this.SECRET = secret;
    }

    /**
     * Gera um JWT de acesso vinculado a uma sessão específica.
     *
     * O sid será validado pelo filtro de autenticação
     * consultando a sessão no banco de dados.
     */
    public String generateToken(
            Usuario usuario,
            Long sessaoId
    ) {
        if (usuario == null || usuario.getId() == null) {
            throw new IllegalArgumentException(
                    "O usuário precisa estar persistido."
            );
        }

        if (sessaoId == null) {
            throw new IllegalArgumentException(
                    "O ID da sessão é obrigatório."
            );
        }

        List<String> authorities = usuario.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Map<String, Object> claims = new HashMap<>();
        claims.put("authorities", authorities);
        claims.put("purpose", ACCESS);
        claims.put("sid", sessaoId);

        return createToken(
                claims,
                usuario.getId().toString(),
                TOKEN_EXPIRATION
        );
    }

    /**
     * Compatibilidade temporária com chamadas antigas.
     *
     * Migrar todos os chamadores para generateToken(usuario, sessaoId)
     * antes de exigir o sid no filtro JWT.
     */
    @Deprecated
    public String generateToken(Usuario usuario) {
        List<String> authorities = usuario.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Map<String, Object> claims = new HashMap<>();
        claims.put("authorities", authorities);
        claims.put("purpose", ACCESS);

        return createToken(
                claims,
                usuario.getId().toString(),
                TOKEN_EXPIRATION
        );
    }

    /**
     * Gera um JWT usado exclusivamente para confirmar e-mail.
     */
    public String generateEmailVerificationToken(
            Usuario usuario
    ) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("purpose", EMAIL_VERIFICATION);

        return createToken(
                claims,
                usuario.getId().toString(),
                EMAIL_TOKEN_EXPIRATION
        );
    }

    private String createToken(
            Map<String, Object> claims,
            String id,
            Duration expiration
    ) {
        Instant now = Instant.now();

        return Jwts.builder()
                .claims(claims)
                .subject(id)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(getSignKey())
                .compact();
    }

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(SECRET)
        );
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractPurpose(String token) {
        return extractClaim(
                token,
                claims -> claims.get("purpose", String.class)
        );
    }

    /**
     * Extrai o ID da sessão (sid) do JWT de acesso.
     */
    public Long extractSessionId(String token) {
        return extractClaim(token, claims -> {
            Object sid = claims.get("sid");

            if (sid instanceof Number numero) {
                return numero.longValue();
            }

            if (sid instanceof String texto) {
                try {
                    return Long.valueOf(texto);
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            return null;
        });
    }

    public <T> T extractClaim(
            String token,
            Function<Claims, T> claimsResolver
    ) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Validação básica do JWT de acesso.
     * A verificação da sessão no banco será integrada ao filtro.
     */
    public Boolean validateToken(
            String token,
            Usuario usuario
    ) {
        String username = extractSubject(token);

        return ACCESS.equals(extractPurpose(token))
                && username.equals(usuario.getId().toString())
                && !isTokenExpired(token);
    }

    /**
     * Valida exclusivamente JWTs de confirmação de e-mail.
     */
    public Boolean validateEmailVerificationToken(
            String token,
            Usuario usuario
    ) {
        String username = extractSubject(token);

        return EMAIL_VERIFICATION.equals(extractPurpose(token))
                && username.equals(usuario.getId().toString())
                && !isTokenExpired(token);
    }
}
