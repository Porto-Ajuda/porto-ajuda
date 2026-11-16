package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;
import java.util.List;

/** Minimal, membership-scoped information explaining why NGO mutations are blocked. */
public record ModerationRestrictionNoticeDTO(
        String subjectType,
        Integer subjectId,
        String subjectLabel,
        long score,
        boolean temporaryRestrictionActive,
        Instant temporaryRestrictionStartedAt,
        Instant temporaryRestrictionEndsAt,
        boolean permanentRestrictionActive,
        Instant serverTime,
        List<Reason> reasons) {

    public record Reason(Long decisionHistoryId, String origin, String justification) { }
}
