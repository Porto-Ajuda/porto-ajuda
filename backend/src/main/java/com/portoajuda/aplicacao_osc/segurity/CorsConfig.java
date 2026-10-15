
    package com.portoajuda.aplicacao_osc.segurity;

    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.web.cors.CorsConfiguration;
    import org.springframework.web.cors.CorsConfigurationSource;
    import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

    import java.util.Arrays;
    import java.util.List;

    @Configuration
    public class CorsConfig {

        @Value("${ALLOWED_ORIGINS}")
        private String allowedOrigins;

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration config = new CorsConfiguration();

            config.setAllowedOrigins(
                    Arrays.stream(allowedOrigins.split(","))
                            .map(String::trim)
                            .filter(origin -> !origin.isEmpty())
                            .toList());

            config.setAllowedMethods(List.of(
                    "GET", "POST", "PUT", "DELETE",
                    "PATCH", "OPTIONS"));

            config.setAllowedHeaders(List.of("*"));

            // Necessário porque o frontend utiliza credentials: 'include'.
            config.setAllowCredentials(true);

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

            source.registerCorsConfiguration("/**", config);

            return source;
        }
    }
