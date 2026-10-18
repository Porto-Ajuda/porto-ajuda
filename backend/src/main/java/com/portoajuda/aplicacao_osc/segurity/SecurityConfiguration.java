package com.portoajuda.aplicacao_osc.segurity;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(
                    CookieCsrfTokenRepository.withHttpOnlyFalse()
                )
                .csrfTokenRequestHandler(
                    new CsrfTokenRequestAttributeHandler()
                )
            )

            .cors(cors -> {})

            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(auth -> auth

                // Arquivos e páginas públicas
                .requestMatchers(
                    "/",
                    "/apoie",
                    "/contato",
                    "/login",
                    "/sobre",
                    "/iniciativas",
                    "/contribuicao",
                    "/doacao-ong",
                    "/oscs",
                    "/proximidade",
                    "/apoio",
                    "/ong-profile",
                    "/cadastro-ong",
                    "/pages/**",
                    "/assets/**",
                    "/styles/**",
                    "/components/**",
                    "/doom/**"
                ).permitAll()

                // Rotas públicas de autenticação
                .requestMatchers(
                    HttpMethod.GET,
                    "/usuario/csrf"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.POST,
                    "/usuario/register",
                    "/usuario/login",
                    "/usuario/confirmar-email",
                    "/usuario/trocar-codigo",
                    "/usuario/reenviar-confirmacao",
                    "/usuario/refresh"
                ).permitAll()

                // Documentação
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/v3/api-docs",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()

                // Outras rotas públicas
                .requestMatchers(
                    "/error",
                    "/api/config/**",
                    "/osc/list"
                ).permitAll()

                // Preflight CORS
                .requestMatchers(
                    HttpMethod.OPTIONS, "/**"
                ).permitAll()

                // Demais rotas exigem autenticação
                .anyRequest().authenticated()
            )

            .addFilterBefore(
                jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}