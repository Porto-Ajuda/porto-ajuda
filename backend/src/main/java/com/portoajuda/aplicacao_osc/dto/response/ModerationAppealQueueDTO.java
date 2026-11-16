package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;
import java.util.List;

public record ModerationAppealQueueDTO(int page, int size, long totalElements, int totalPages,
        List<Item> content) {
    public record Item(long id, String subjectType, int subjectId, String subjectLabel,
            String appellantLabel, long decisionHistoryId, String justification, String status,
            int pointsReversed, int pointsRemaining, Instant createdAt, Instant reviewedAt) { }
}
