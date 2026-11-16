package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;
import java.util.List;

public record ModerationSubjectDTO(
        String subjectType,
        Integer subjectId,
        String subjectLabel,
        long score,
        boolean temporaryRestrictionActive,
        Instant temporaryRestrictionEndsAt,
        boolean permanentRestrictionActive,
        List<PointEntry> pointsHistory,
        List<Appeal> appeals,
        Instant temporaryRestrictionStartedAt,
        Instant serverTime,
        boolean hasPointHistory) {
    public ModerationSubjectDTO(String subjectType, Integer subjectId, String subjectLabel, long score,
            boolean temporaryRestrictionActive, Instant temporaryRestrictionEndsAt,
            boolean permanentRestrictionActive, List<PointEntry> pointsHistory, List<Appeal> appeals) {
        this(subjectType, subjectId, subjectLabel, score, temporaryRestrictionActive,
                temporaryRestrictionEndsAt, permanentRestrictionActive, pointsHistory, appeals, null,
                null, !pointsHistory.isEmpty());
    }

    public record PointEntry(long id, int delta, String origin, String justification, Instant occurredAt,
            String actorProfile, Long decisionHistoryId, String decisionJustification,
            String targetType, Integer targetId, Instant decisionAt) { }
    public record Appeal(long id, long decisionHistoryId, String decisionJustification,
            String justification, String status, String response, int pointsReversed,
            Instant createdAt, Instant reviewedAt) { }
}
