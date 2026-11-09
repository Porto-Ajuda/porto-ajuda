package com.portoajuda.aplicacao_osc.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminAssignmentDTO(
        UUID id,
        Integer userId,
        AdminProfile profile,
        AdminAssignmentStatus status,
        Instant grantedAt,
        Integer grantedByUserId,
        Instant lastModifiedAt,
        Integer lastModifiedByUserId,
        Instant revokedAt,
        String reason) {

    public static AdminAssignmentDTO from(AdminAssignment assignment) {
        return new AdminAssignmentDTO(
                assignment.id(),
                assignment.userId() == null ? assignment.userIdSnapshot() : assignment.userId(),
                assignment.profile(),
                assignment.status(),
                assignment.grantedAt(),
                assignment.grantedByUserId(),
                assignment.lastModifiedAt(),
                assignment.lastModifiedByUserId(),
                assignment.revokedAt(),
                assignment.reason());
    }
}
