package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;
import java.util.List;

public record AdminDashboardOverviewDTO(
        long registeredUsers,
        long activeOrganizations,
        long inactiveOrganizations,
        long archivedOrganizations,
        long publications,
        long volunteerOpportunities,
        Instant generatedAt,
        boolean recentActivityAvailable,
        List<AdminDashboardActivityDTO> recentActivity) {

    public AdminDashboardOverviewDTO {
        recentActivity = List.copyOf(recentActivity);
    }
}
