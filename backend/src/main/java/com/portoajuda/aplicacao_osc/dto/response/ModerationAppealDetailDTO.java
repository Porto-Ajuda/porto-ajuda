package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;

public record ModerationAppealDetailDTO(long id,String subjectType,int subjectId,String subjectLabel,
        String appellantLabel,long decisionHistoryId,String originalDecisionJustification,int originalPoints,
        int pointsReversed,int pointsRemaining,long subjectScore,String appealJustification,String status,String response,
        Instant createdAt,Instant reviewedAt) { }
