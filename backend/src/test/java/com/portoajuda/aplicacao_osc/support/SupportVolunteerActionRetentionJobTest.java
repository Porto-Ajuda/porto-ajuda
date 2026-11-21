package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

class SupportVolunteerActionRetentionJobTest {
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Test void retentionBeanRunsWithConversationalFeatureDisabled() {
        SupportVolunteerApplicationDraftRepository repository = mock(SupportVolunteerApplicationDraftRepository.class);
        when(repository.purgeExpiredActionData(any())).thenReturn(3);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
                    "app.support-assistant.enabled", "false")));
            context.registerBean(SupportVolunteerApplicationDraftRepository.class, () -> repository);
            context.register(SupportVolunteerActionRetentionJob.class);
            context.refresh();

            assertNotNull(context.getBean(SupportVolunteerActionRetentionJob.class));
            assertEquals(3, context.getBean(SupportVolunteerActionRetentionJob.class).purgeExpiredActionData());
        }
        verify(repository).purgeExpiredActionData(any());
    }

    @Test void cleanupIsANoopUntilTheManualMigrationCreatesBothTables() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        var repository = new SupportVolunteerApplicationDraftRepository(jdbc);

        assertEquals(0, repository.purgeExpiredActionData(NOW));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void configuredCleanupIntervalDefaultsToOneMinute() throws Exception {
        Scheduled schedule = SupportVolunteerActionRetentionJob.class
                .getMethod("purgeExpiredActionData").getAnnotation(Scheduled.class);
        assertNotNull(schedule);
        assertEquals("${app.support-assistant.retention-poll-ms:60000}", schedule.fixedDelayString());
    }
}
