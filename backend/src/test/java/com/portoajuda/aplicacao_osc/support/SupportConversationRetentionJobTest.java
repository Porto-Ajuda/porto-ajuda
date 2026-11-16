package com.portoajuda.aplicacao_osc.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class SupportConversationRetentionJobTest {
    @Test void usesInjectedClockAndBoundedBatchForTheProductRetentionWorker() {
        SupportConversationRepository repository=mock(SupportConversationRepository.class);
        Instant now=Instant.parse("2026-06-01T00:00:00Z"); UUID id=UUID.randomUUID();
        when(repository.expiredForContentRetention(now,100)).thenReturn(List.of(id));
        when(repository.purgeExpiredContent(id,now)).thenReturn(true);
        var job=new SupportConversationRetentionJob(repository,Clock.fixed(now,ZoneOffset.UTC));
        assertEquals(1,job.purgeExpiredBatch());
        verify(repository).expiredForContentRetention(now,100);
        verify(repository).purgeExpiredContent(id,now);
    }

    @Test void retentionSchedulerIsEnabledOnlyWithTheSupportMasterFlag() {
        var condition=SupportConversationRetentionJob.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals("app.support-assistant",condition.prefix());
        assertArrayEquals(new String[] {"enabled"},condition.name());
        assertEquals("true",condition.havingValue());
    }
}
