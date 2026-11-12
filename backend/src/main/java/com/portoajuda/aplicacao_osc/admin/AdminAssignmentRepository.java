package com.portoajuda.aplicacao_osc.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Uses JDBC so Hibernate ddl-auto=validate does not require the pending RBAC tables. */
@Repository
public class AdminAssignmentRepository {

    private static final String SELECT_COLUMNS = """
            id, usuario_id, usuario_id_snapshot, profile, status, granted_at,
            granted_by_user_id_snapshot, last_modified_at,
            last_modified_by_user_id_snapshot, revoked_at, reason
            """;

    private static final RowMapper<AdminAssignment> ROW_MAPPER =
            AdminAssignmentRepository::mapAssignment;

    private final JdbcTemplate jdbcTemplate;

    public AdminAssignmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AdminAssignment> findActiveByUserId(Integer userId) {
        return queryOne("""
                SELECT %s FROM public.admin_assignments
                 WHERE usuario_id = ? AND status = 'ACTIVE'
                """.formatted(SELECT_COLUMNS), userId);
    }

    public Optional<AdminAssignment> findActiveByUserIdForUpdate(Integer userId) {
        return queryOne("""
                SELECT %s FROM public.admin_assignments
                 WHERE usuario_id = ? AND status = 'ACTIVE'
                 FOR UPDATE
                """.formatted(SELECT_COLUMNS), userId);
    }

    public Optional<AdminAssignment> findByIdForUpdate(UUID assignmentId) {
        return queryOne("""
                SELECT %s FROM public.admin_assignments
                 WHERE id = ?
                 FOR UPDATE
                """.formatted(SELECT_COLUMNS), assignmentId);
    }

    public List<AdminAssignment> findAll(int limit, int offset) {
        return jdbcTemplate.query("""
                SELECT %s FROM public.admin_assignments
                 ORDER BY last_modified_at DESC, id
                 LIMIT ? OFFSET ?
                """.formatted(SELECT_COLUMNS), ROW_MAPPER, limit, offset);
    }

    public void insert(AdminAssignment assignment) {
        jdbcTemplate.update("""
                INSERT INTO public.admin_assignments (
                    id, usuario_id, usuario_id_snapshot, profile, status, granted_at,
                    granted_by_user_id_snapshot, last_modified_at,
                    last_modified_by_user_id_snapshot, revoked_at, reason, version
                ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, NULL, ?, 0)
                """,
                assignment.id(), assignment.userId(), assignment.userIdSnapshot(),
                assignment.profile().name(), Timestamp.from(assignment.grantedAt()),
                assignment.grantedByUserId(), Timestamp.from(assignment.lastModifiedAt()),
                assignment.lastModifiedByUserId(), assignment.reason());
    }

    public int revoke(
            UUID assignmentId,
            Integer actorId,
            String reason,
            Instant now) {
        return jdbcTemplate.update("""
                UPDATE public.admin_assignments
                   SET status = 'REVOKED', revoked_at = ?, last_modified_at = ?,
                       last_modified_by_user_id_snapshot = ?, reason = ?, version = version + 1
                 WHERE id = ? AND status = 'ACTIVE'
                """,
                Timestamp.from(now), Timestamp.from(now), actorId, reason, assignmentId);
    }

    public void lockSuperAdminInvariant() {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(1346458434, 1)");
    }

    public long countActiveSuperAdmins() {
        Long count = jdbcTemplate.queryForObject(activeSuperAdminCountSql(), Long.class);
        return count == null ? 0 : count;
    }

    public long countEligibleActiveSuperAdmins() {
        Long count = jdbcTemplate.queryForObject(eligibleSuperAdminCountSql(), Long.class);
        return count == null ? 0 : count;
    }

    public boolean isActiveSuperAdmin(Integer userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM public.admin_assignments a
                  JOIN public.usuarios u ON u.id = a.usuario_id
                  JOIN public.usuario_roles ur ON ur.id_usuario = u.id
                  JOIN public.roles r ON r.id = ur.id_role
                 WHERE a.usuario_id = ?
                   AND a.profile = 'SUPER_ADMIN'
                   AND a.status = 'ACTIVE'
                   AND u.ativo = TRUE
                   AND r.nome = 'ADMIN'
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    public boolean isEligibleActiveSuperAdmin(Integer userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM public.admin_assignments a
                  JOIN public.usuarios u ON u.id = a.usuario_id
                  JOIN public.usuario_roles ur ON ur.id_usuario = u.id
                  JOIN public.roles r ON r.id = ur.id_role
                 WHERE a.usuario_id = ?
                   AND a.profile = 'SUPER_ADMIN'
                   AND a.status = 'ACTIVE'
                   AND u.ativo = TRUE
                   AND u.email_verificado = TRUE
                   AND u.telefone_verificado = TRUE
                   AND u.email IS NOT NULL
                   AND BTRIM(u.email) <> ''
                   AND u.telefone IS NOT NULL
                   AND BTRIM(u.telefone) <> ''
                   AND u.telefone ~ '^[+0-9[:space:]().-]+$'
                   AND regexp_replace(u.telefone, '[^0-9]', '', 'g') ~ '^(55)?[1-9][0-9]9[0-9]{8}$'
                   AND r.nome = 'ADMIN'
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    public boolean isEligibleAdminUser(Integer userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM public.usuarios u
                  JOIN public.usuario_roles ur ON ur.id_usuario = u.id
                  JOIN public.roles r ON r.id = ur.id_role
                 WHERE u.id = ?
                   AND u.ativo = TRUE
                   AND u.email_verificado = TRUE
                   AND u.telefone_verificado = TRUE
                   AND u.email IS NOT NULL
                   AND BTRIM(u.email) <> ''
                   AND u.telefone IS NOT NULL
                   AND BTRIM(u.telefone) <> ''
                   AND u.telefone ~ '^[+0-9[:space:]().-]+$'
                   AND regexp_replace(u.telefone, '[^0-9]', '', 'g') ~ '^(55)?[1-9][0-9]9[0-9]{8}$'
                   AND r.nome = 'ADMIN'
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    private Optional<AdminAssignment> queryOne(String sql, Object parameter) {
        List<AdminAssignment> rows = jdbcTemplate.query(sql, ROW_MAPPER, parameter);
        return rows.stream().findFirst();
    }

    private static String activeSuperAdminCountSql() {
        return """
                SELECT COUNT(DISTINCT u.id)
                  FROM public.admin_assignments a
                  JOIN public.usuarios u ON u.id = a.usuario_id
                  JOIN public.usuario_roles ur ON ur.id_usuario = u.id
                  JOIN public.roles r ON r.id = ur.id_role
                 WHERE a.profile = 'SUPER_ADMIN'
                   AND a.status = 'ACTIVE'
                   AND u.ativo = TRUE
                   AND r.nome = 'ADMIN'
                """;
    }

    private static String eligibleSuperAdminCountSql() {
        return """
                SELECT COUNT(DISTINCT u.id)
                  FROM public.admin_assignments a
                  JOIN public.usuarios u ON u.id = a.usuario_id
                  JOIN public.usuario_roles ur ON ur.id_usuario = u.id
                  JOIN public.roles r ON r.id = ur.id_role
                 WHERE a.profile = 'SUPER_ADMIN'
                   AND a.status = 'ACTIVE'
                   AND u.ativo = TRUE
                   AND u.email_verificado = TRUE
                   AND u.telefone_verificado = TRUE
                   AND u.email IS NOT NULL
                   AND BTRIM(u.email) <> ''
                   AND u.telefone IS NOT NULL
                   AND BTRIM(u.telefone) <> ''
                   AND u.telefone ~ '^[+0-9[:space:]().-]+$'
                   AND regexp_replace(u.telefone, '[^0-9]', '', 'g') ~ '^(55)?[1-9][0-9]9[0-9]{8}$'
                   AND r.nome = 'ADMIN'
                """;
    }

    private static AdminAssignment mapAssignment(ResultSet row, int rowNumber) throws SQLException {
        return new AdminAssignment(
                row.getObject("id", UUID.class),
                nullableInteger(row, "usuario_id"),
                row.getInt("usuario_id_snapshot"),
                AdminProfile.valueOf(row.getString("profile")),
                AdminAssignmentStatus.valueOf(row.getString("status")),
                instant(row, "granted_at"),
                nullableInteger(row, "granted_by_user_id_snapshot"),
                instant(row, "last_modified_at"),
                nullableInteger(row, "last_modified_by_user_id_snapshot"),
                instant(row, "revoked_at"),
                row.getString("reason"));
    }

    private static Integer nullableInteger(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
