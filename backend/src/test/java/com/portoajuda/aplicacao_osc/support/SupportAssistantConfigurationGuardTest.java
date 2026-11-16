package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import com.portoajuda.aplicacao_osc.service.GeminiRequestBudget;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class SupportAssistantConfigurationGuardTest {
    @Test void enabledGeminiAdapterUsesItsExplicitProductionConstructor() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
                .withUserConfiguration(GeminiSupportAssistantProvider.class, GeminiRequestBudget.class)
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withPropertyValues(
                        "app.support-assistant.enabled=true",
                        "app.support-assistant.ai.enabled=true",
                        "app.support-assistant.ai.provider=gemini",
                        "gemini.api-key=synthetic-test-key")
                .withBean(tools.jackson.databind.ObjectMapper.class, tools.jackson.databind.ObjectMapper::new)
                .run(context -> {
                    assertNull(context.getStartupFailure(), () -> String.valueOf(context.getStartupFailure()));
                    assertNotNull(context.getBean(GeminiSupportAssistantProvider.class));
                    assertNotNull(context.getBean(RestClient.Builder.class));
                });
    }

    @Test void disabledDefaultDoesNotQuerySupportTablesBeforeOrAfterMigration() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        ObjectProvider<SupportAssistantProvider> providers=mock(ObjectProvider.class);
        new SupportAssistantConfigurationGuard(false,false,"none","",providers,jdbc)
                .run(new DefaultApplicationArguments(new String[0]));
        verifyNoInteractions(jdbc,providers);
    }

    @Test void enabledSupportFailsClosedIfRetentionSchemaIsIncomplete() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        ObjectProvider<SupportAssistantProvider> providers=mock(ObjectProvider.class);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(false);
        var guard=new SupportAssistantConfigurationGuard(true,false,"none","",providers,jdbc);
        assertThrows(IllegalStateException.class,()->guard.run(new DefaultApplicationArguments(new String[0])));
        verify(jdbc).queryForObject(anyString(),eq(Boolean.class));
        verifyNoInteractions(providers);
    }
}
