package com.portoajuda.aplicacao_osc.support;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Purges only closed conversations past their closure-based retention deadline. */
@Component
@ConditionalOnProperty(prefix="app.support-assistant", name="enabled", havingValue="true")
public class SupportConversationRetentionJob {
    private static final Logger log=LoggerFactory.getLogger(SupportConversationRetentionJob.class);
    private static final int BATCH_SIZE=100;
    private final SupportConversationRepository repository;
    private final Clock clock;

    @Autowired
    public SupportConversationRetentionJob(SupportConversationRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SupportConversationRetentionJob(SupportConversationRepository repository, Clock clock) {
        this.repository=repository; this.clock=clock;
    }

    @Scheduled(fixedDelayString="${app.support-assistant.retention-poll-ms:60000}")
    public void scheduledPurge() { purgeExpiredBatch(); }

    public int purgeExpiredBatch() {
        Instant now=clock.instant();
        int purged=0;
        int failed=0;
        for(UUID id: repository.expiredForContentRetention(now,BATCH_SIZE)) {
            try { if(repository.purgeExpiredContent(id,now))purged++; }
            catch(DataAccessException failure) { failed++; }
        }
        if(failed>0)log.warn("Support retention batch had {} purge failure(s); eligible records will be retried on the next poll.",failed);
        return purged;
    }
}
