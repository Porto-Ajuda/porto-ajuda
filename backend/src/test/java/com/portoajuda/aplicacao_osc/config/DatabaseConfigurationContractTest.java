package com.portoajuda.aplicacao_osc.config;

import com.portoajuda.aplicacao_osc.controller.ConfigController;
import com.portoajuda.aplicacao_osc.service.LimpezaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigurationContractTest {

    private static final String[] REQUIRED_DATABASE_VARIABLES = {
            "DB_URL", "DB_USER", "DB_PASSWORD"
    };

    @Test
    void umaConfiguracaoSupabaseEUsadaSemDependenciaDePerfil() throws IOException {
        Properties properties = applicationProperties();

        assertEquals("${DB_URL}", properties.getProperty("spring.datasource.url"));
        assertEquals("${DB_USER}", properties.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", properties.getProperty("spring.datasource.password"));
        assertEquals("optional:file:./.env[.properties],optional:file:../.env[.properties]",
                properties.getProperty("spring.config.import"));
        assertEquals("require", properties.getProperty("spring.datasource.hikari.data-source-properties.sslmode"));
        assertNull(properties.getProperty("spring.profiles.active"));
        assertEquals("SUPABASE", new ConfigController().database().get("status"));
        assertNull(getClass().getResource("/application-local.properties"));
        assertNull(getClass().getResource("/application-prod.properties"));
    }

    @Test
    void variaveisDeConexaoSaoObrigatoriasESemFallback() throws IOException {
        Properties properties = applicationProperties();

        assertEquals("${DB_URL}", properties.getProperty("spring.datasource.url"));
        assertEquals("${DB_USER}", properties.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", properties.getProperty("spring.datasource.password"));
        for (String variable : REQUIRED_DATABASE_VARIABLES) {
            assertFalse(properties.values().stream().anyMatch(value ->
                    value.toString().contains("${" + variable + ":")));
        }
        assertFalse(properties.stringPropertyNames().stream().anyMatch(name -> name.contains("DB_LOCAL")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/portoajuda/aplicacao_osc/config/DatabaseConfig.java")));

        PropertySourcesPropertyResolver resolver =
                new PropertySourcesPropertyResolver(new MutablePropertySources());
        for (String variable : REQUIRED_DATABASE_VARIABLES) {
            String placeholder = properties.getProperty(springPropertyFor(variable));
            IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                    () -> resolver.resolveRequiredPlaceholders(placeholder));
            assertTrue(erro.getMessage().contains(variable));
        }
    }

    @Test
    void schemaPermaneceEmValidacaoESqlDeAplicacaoNaoEExibido() throws IOException {
        Properties properties = applicationProperties();

        assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("false", properties.getProperty("spring.jpa.show-sql"));
        assertEquals("OFF", properties.getProperty("logging.level.org.hibernate.orm.connections.pooling"));
        assertEquals("OFF", properties.getProperty("logging.level.com.zaxxer.hikari"));
        assertFalse(properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith("logging.level."))
                .anyMatch(name -> name.toLowerCase().contains("password")
                        || name.toLowerCase().contains("credential")));
    }

    @Test
    void semeioDePapeisExigeOpcaoExplicita() throws IOException {
        ConditionalOnProperty condition = DataInitializer.class.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(condition);
        assertEquals("app.initialization", condition.prefix());
        assertTrue(java.util.Arrays.asList(condition.name()).contains("seed-roles"));
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
        assertEquals("${APP_INITIALIZATION_SEED_ROLES:false}",
                applicationProperties().getProperty("app.initialization.seed-roles"));
    }

    @Test
    void limpezaAgendadaFicaDesativadaSemCronExplicito() throws IOException, NoSuchMethodException {
        Scheduled scheduled = LimpezaService.class
                .getMethod("executarLimpezaAutomatica")
                .getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("${app.maintenance.cleanup-cron:-}", scheduled.cron());
        Properties properties = applicationProperties();
        assertEquals("${APP_MAINTENANCE_CLEANUP_CRON:-}",
                properties.getProperty("app.maintenance.cleanup-cron"));
        PropertySourcesPropertyResolver resolver =
                new PropertySourcesPropertyResolver(new MutablePropertySources());
        assertEquals("-", resolver.resolveRequiredPlaceholders(
                properties.getProperty("app.maintenance.cleanup-cron")));
    }

    private String springPropertyFor(String variable) {
        return switch (variable) {
            case "DB_URL" -> "spring.datasource.url";
            case "DB_USER" -> "spring.datasource.username";
            case "DB_PASSWORD" -> "spring.datasource.password";
            default -> throw new IllegalArgumentException("Variável de teste desconhecida.");
        };
    }

    private Properties applicationProperties() throws IOException {
        Properties properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(stream);
            properties.load(stream);
        }
        return properties;
    }
}
