package com.portoajuda.aplicacao_osc.dto.response;

import java.util.List;

public record ModerationEntityPageDTO(int page, int size, long totalElements, int totalPages,
        String category, List<Item> content) {
    public record Item(String type, int id, String label, String relatedLabel, String status,
            String statusOrigin, boolean archived,
            Integer authorUserId, String authorLabel, String opportunityModality, String opportunityLocation,
            Integer opportunityVacancies, long pendingReports, long score, boolean temporaryRestrictionActive,
            java.time.Instant temporaryRestrictionEndsAt, boolean permanentRestrictionActive,
            boolean automaticProtectionActive, boolean administrativeContentRestrictionActive) { }
}
