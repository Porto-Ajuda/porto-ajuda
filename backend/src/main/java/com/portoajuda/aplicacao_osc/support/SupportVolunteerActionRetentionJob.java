package com.portoajuda.aplicacao_osc.support;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes five-minute previews and 30-day retry receipts even for conversations without messages. */
@Component
public class SupportVolunteerActionRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(SupportVolunteerActionRetentionJob.class);
    private final SupportVolunteerApplicationDraftRepository drafts;
    private final Clock clock;

    @Autowired
    public SupportVolunteerActionRetentionJob(SupportVolunteerApplicationDraftRepository drafts) {
        this(drafts, Clock.systemUTC());
    }

    SupportVolunteerActionRetentionJob(SupportVolunteerApplicationDraftRepository drafts, Clock clock) {
        this.drafts = drafts;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString="${app.support-assistant.retention-poll-ms:60000}")
    public int purgeExpiredActionData() {
        try {
            return drafts.purgeExpiredActionData(clock.instant());
        } catch (DataAccessException failure) {
            // A manual migration or a transient database outage must not stop application startup
            // or permanently suppress subsequent scheduled sweeps.
            log.warn("Sophie action retention sweep could not access its tables; the next scheduled sweep will retry.");
            return 0;
        }
    }
}
