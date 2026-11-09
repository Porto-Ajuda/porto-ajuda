package com.portoajuda.aplicacao_osc.admin;

import java.time.Instant;
import java.util.UUID;

/** JDBC-backed model; intentionally not a JPA entity while RBAC migration is pending. */
public record AdminAssignment(
        UUID id,
        Integer userId,
        Integer userIdSnapshot,
        AdminProfile profile,
        AdminAssignmentStatus status,
        Instant grantedAt,
        Integer grantedByUserId,
        Instant lastModifiedAt,
        Integer lastModifiedByUserId,
        Instant revokedAt,
        String reason) {
}
