package com.portoajuda.aplicacao_osc.segurity;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
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
    private static final Duration TOKEN_EXPIRATION = Duration.ofMinutes(30);

    public JwtService(@Value("${JWT_SECRET_KEY}") String secret) {
        this.SECRET = secret;
    }

    public String generateToken(Usuario usuario) {

        List<String> authorities = usuario.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Map<String, Object> claims = new HashMap<>();
        claims.put("authorities", authorities);

        return createToken(claims, usuario.getId().toString());
    }

    private String createToken(Map<String, Object> claims, String id){
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(id)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_EXPIRATION)))
                .signWith(getSignKey())
                .compact();

    }

    private SecretKey getSignKey(){
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    public String extractSubject(String token){
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token){
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver){
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token){
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token, Usuario usuario) {
        final String username = extractSubject(token);
        return (username.equals(usuario.getId().toString()) && !isTokenExpired(token));
    }
}