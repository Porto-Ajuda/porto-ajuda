// package com.portoajuda.aplicacao_osc.segurity;

// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.http.HttpMethod;
// import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
// import org.springframework.security.config.annotation.web.builders.HttpSecurity;
// import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
// import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
// import org.springframework.security.config.http.SessionCreationPolicy;
// import org.springframework.security.web.SecurityFilterChain;
// import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// import lombok.RequiredArgsConstructor;

// @Configuration
// @RequiredArgsConstructor
// @EnableWebSecurity
// @EnableMethodSecurity
// public class SecurityConfiguration {

//         private final JwtAuthFilter jwtAuthFilter;

//         @Bean
//         public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

//                 http
//                                 .csrf(AbstractHttpConfigurer::disable)

//                                 .cors(cors -> {
//                                 })

//                                 .headers(headers -> headers
//                                                 .frameOptions(frame -> frame.sameOrigin()))

//                                 .sessionManagement(session -> session
//                                                 .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

//                                 .authorizeHttpRequests(auth -> auth

                                                // ========================================
                                                // FRONTEND - PÚBLICO
                                                // ========================================

                                                // .requestMatchers(
                                                //                 "/",
                                                //                 "/apoie",
                                                //                 "/contato",
                                                //                 "/login",
                                                //                 "/sobre",
                                                //                 "/iniciativas",
                                                //                 "/contribuicao",
                                                //                 "/doacao-ong",
                                                //                 "/perfil",
                                                //                 "/oscs",
                                                //                 "/proximidade",
                                                //                 "/apoio",
                                                //                 "/ong-profile",
                                                //                 "/cadastro-ong",

                                                //                 "/pages/**",
                                                //                 "/assets/**",
                                                //                 "/styles/**",
                                                //                 "/components/**",
                                                //                 "/doom/**",

                                                //                 "/error",
                                                //                 "/usuario/register",
                                                //                 "/usuario/login",
                                                //                 "/swagger-ui.html",
                                                //                 "/swagger-ui/**",
                                                //                 "/v3/api-docs/**",
                                                //                 "/v3/api-docs",
                                                //                 "/swagger-resources/**",
                                                //                 "/webjars/**",
                                                //                 "/api/config/**",
                                                //                 "/osc/list")
                                                // .permitAll()

                                                // ========================================
                                                // CORS / PREFLIGHT
                                                // ========================================

                                                // .requestMatchers(HttpMethod.OPTIONS, "/**")
                                                // .permitAll()

                                                // ========================================
                                                // RESTANTE DA API - PROTEGIDO
                                                // ========================================

//                                                 .anyRequest()
//                                                 .authenticated())

//                                 .addFilterBefore(
//                                                 jwtAuthFilter,
//                                                 UsernamePasswordAuthenticationFilter.class);

//                 return http.build();
//         }
// }

// Quando estiver pronto, devemos tirar o comentário do código e apagar o que esta em baixo para que ele seja compilado e funcione corretamente.

package com.portoajuda.aplicacao_osc.segurity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{
        http.
                csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/",
                                "/error",
                                "/usuario/register",
                                "/usuario/login",        
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/osc/list")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .anyRequest().authenticated())

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
