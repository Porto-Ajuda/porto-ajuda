package com.portoajuda.aplicacao_osc.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Opt-in service integration test for the explicitly disposable loopback RBAC test database.
 * The database schema must be provisioned from the test fixture, never from production.
 */
class AdminAssignmentServicePostgresIntegrationTest {

    private static final String DATABASE_NAME = "porto_admin_auth_test";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignmentProfilesChangesRevocationAuthorizationAndAuditUsePostgres() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        AdminAssignmentRepository assignments = new AdminAssignmentRepository(jdbc);
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);
        AdminRbacInvariantService invariant = new AdminRbacInvariantService(assignments, authorization);
        AdminOperationAuditService audit = new AdminOperationAuditService(jdbc, authorization);
        AdminPrivilegedSessionService sessions = mock(AdminPrivilegedSessionService.class);
        UsuarioRepository users = mock(UsuarioRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        Integer actorId = jdbc.queryForObject(
                "SELECT usuario_id FROM public.admin_assignments WHERE profile='SUPER_ADMIN' AND status='ACTIVE' ORDER BY granted_at LIMIT 1",
                Integer.class);
        assertNotNull(actorId, "The isolated fixture must seed one eligible SUPER_ADMIN actor.");
        AdminAssignment actorAssignment = assignments.findActiveByUserId(actorId).orElseThrow();
        assertEquals(AdminProfile.SUPER_ADMIN, actorAssignment.profile());
        int auditEventsBefore = jdbc.queryForObject(
                "SELECT count(*) FROM public.admin_operation_audit_events WHERE actor_user_id_snapshot=?",
                Integer.class, actorId);

        // Keep the correlation suffix below the sanitizer's opaque-credential threshold.
        String prefix = "phase-b-it-" + UUID.randomUUID().toString().substring(0, 8);
        List<Integer> targetIds = new ArrayList<>();
        Map<Integer, Usuario> targetUsers = new HashMap<>();
        try {
            Map<AdminProfile, Integer> userIdsByProfile = new HashMap<>();
            for (AdminProfile profile : AdminProfile.values()) {
                int userId = createAdminUser(jdbc, "grant-" + profile.name().toLowerCase());
                targetIds.add(userId);
                targetUsers.put(userId, adminUser(userId));
                userIdsByProfile.put(profile, userId);
            }
            when(users.findByIdForUpdate(anyInt())).thenAnswer(invocation ->
                    Optional.ofNullable(targetUsers.get(invocation.getArgument(0))));
            when(users.findById(anyInt())).thenAnswer(invocation ->
                    Optional.ofNullable(targetUsers.get(invocation.getArgument(0))));
            when(users.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AdminAssignmentService service = new AdminAssignmentService(
                    assignments, authorization, invariant, audit, sessions, users, roles);
            authenticate(adminUser(actorId));

            Map<AdminProfile, AdminAssignmentDTO> created = new HashMap<>();
            for (AdminProfile profile : AdminProfile.values()) {
                int userId = userIdsByProfile.get(profile);
                AdminAssignmentDTO result = transaction.execute(status -> service.assignOrChange(
                        new AdminAssignmentRequest(userId, profile, prefix + "-grant-" + profile.name())));
                assertNotNull(result);
                assertEquals(profile, result.profile());
                assertEquals(AdminAssignmentStatus.ACTIVE, result.status());
                created.put(profile, result);
                assertEquals(profile, assignments.findActiveByUserId(userId).orElseThrow().profile());
            }

            AdminAssignmentDTO changed = transaction.execute(status -> service.changeProfile(
                    created.get(AdminProfile.PLATFORM_ADMIN).id(),
                    new AdminAssignmentProfileRequest(AdminProfile.AUDITOR, prefix + "-profile-change")));
            assertEquals(AdminProfile.AUDITOR, changed.profile());
            assertEquals(AdminAssignmentStatus.REVOKED,
                    assignments.findByIdForUpdate(created.get(AdminProfile.PLATFORM_ADMIN).id()).orElseThrow().status());
            assertEquals(AdminProfile.AUDITOR,
                    assignments.findActiveByUserId(userIdsByProfile.get(AdminProfile.PLATFORM_ADMIN))
                            .orElseThrow().profile());

            AdminAssignmentDTO revoked = transaction.execute(status -> service.revoke(
                    created.get(AdminProfile.MODERATOR).id(),
                    new AdminAssignmentRevokeRequest(prefix + "-revoke")));
            assertEquals(AdminAssignmentStatus.REVOKED, revoked.status());
            assertTrue(assignments.findActiveByUserId(userIdsByProfile.get(AdminProfile.MODERATOR)).isEmpty());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM public.usuarios WHERE id=? AND ativo=TRUE",
                    Integer.class, userIdsByProfile.get(AdminProfile.MODERATOR)));
            assertTrue(targetUsers.get(userIdsByProfile.get(AdminProfile.MODERATOR)).getRoles().stream()
                    .noneMatch(role -> "ADMIN".equals(role.getNome())));

            int auditEvents = jdbc.queryForObject(
                    "SELECT count(*) FROM public.admin_operation_audit_events WHERE actor_user_id_snapshot=?",
                    Integer.class, actorId);
            assertEquals(6, auditEvents - auditEventsBefore,
                    "Four grants, one profile change, and one revocation are audited.");

            for (Map.Entry<AdminProfile, Integer> entry : userIdsByProfile.entrySet()) {
                int expectedRevocations = 1;
                if (entry.getKey() == AdminProfile.PLATFORM_ADMIN
                        || entry.getKey() == AdminProfile.MODERATOR) {
                    expectedRevocations++;
                }
                verify(sessions, times(expectedRevocations))
                        .revogarTodasDoUsuario(entry.getValue(), "ADMIN_REVOKED");
            }

            assertThrows(AccessDeniedException.class, () -> transaction.execute(status ->
                    service.assignOrChange(new AdminAssignmentRequest(actorId, AdminProfile.MODERATOR,
                            prefix + "-self-grant"))));
            assertThrows(AccessDeniedException.class, () -> transaction.execute(status ->
                    service.changeProfile(actorAssignment.id(),
                            new AdminAssignmentProfileRequest(AdminProfile.PLATFORM_ADMIN, prefix + "-self-change"))));
            assertThrows(AccessDeniedException.class, () -> transaction.execute(status ->
                    service.revoke(actorAssignment.id(), new AdminAssignmentRevokeRequest(prefix + "-self-revoke"))));

            int auditorId = userIdsByProfile.get(AdminProfile.AUDITOR);
            authenticate(adminUser(auditorId));
            long activeBeforeDeniedRequest = jdbc.queryForObject(
                    "SELECT count(*) FROM public.admin_assignments WHERE status='ACTIVE'", Long.class);
            assertThrows(AccessDeniedException.class, () -> transaction.execute(status ->
                    service.assignOrChange(new AdminAssignmentRequest(2147483000, AdminProfile.MODERATOR,
                            prefix + "-unauthorized"))));
            assertEquals(activeBeforeDeniedRequest, jdbc.queryForObject(
                    "SELECT count(*) FROM public.admin_assignments WHERE status='ACTIVE'", Long.class));
        } finally {
            for (Integer userId : targetIds) {
                jdbc.update("DELETE FROM public.admin_assignments WHERE usuario_id_snapshot=?", userId);
                jdbc.update("DELETE FROM public.usuario_roles WHERE id_usuario=?", userId);
                jdbc.update("DELETE FROM public.usuarios WHERE id=?", userId);
            }
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void assignmentRollsBackWhenAuditInsertFails() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        AdminAssignmentRepository assignments = new AdminAssignmentRepository(jdbc);
        AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);
        AdminRbacInvariantService invariant = new AdminRbacInvariantService(assignments, authorization);
        AdminOperationAuditService audit = new AdminOperationAuditService(jdbc, authorization);
        UsuarioRepository users = mock(UsuarioRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        AdminPrivilegedSessionService sessions = mock(AdminPrivilegedSessionService.class);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        Integer actorId = jdbc.queryForObject(
                "SELECT usuario_id FROM public.admin_assignments WHERE profile='SUPER_ADMIN' AND status='ACTIVE' ORDER BY granted_at LIMIT 1",
                Integer.class);
        assertNotNull(actorId);
        Integer targetId = createAdminUser(jdbc, "audit-failure");
        Usuario target = adminUser(targetId);
        when(users.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));
        when(users.findById(targetId)).thenReturn(Optional.of(target));
        when(users.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AdminAssignmentService service = new AdminAssignmentService(
                assignments, authorization, invariant, audit, sessions, users, roles);

        jdbc.execute("""
                CREATE FUNCTION public.phase_b_fail_audit_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced test audit failure'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER phase_b_fail_audit_insert BEFORE INSERT ON public.admin_operation_audit_events
                FOR EACH ROW EXECUTE FUNCTION public.phase_b_fail_audit_insert()
                """);
        try {
            authenticate(adminUser(actorId));
            assertThrows(DataAccessException.class, () -> transaction.execute(status ->
                    service.assignOrChange(new AdminAssignmentRequest(
                            targetId, AdminProfile.MODERATOR, "PA-54321"))));
            assertTrue(assignments.findActiveByUserId(targetId).isEmpty(),
                    "The assignment insert must roll back with the failed audit insert.");
        } finally {
            jdbc.execute("DROP TRIGGER phase_b_fail_audit_insert ON public.admin_operation_audit_events");
            jdbc.execute("DROP FUNCTION public.phase_b_fail_audit_insert()");
            jdbc.update("DELETE FROM public.admin_assignments WHERE usuario_id_snapshot=?", targetId);
            jdbc.update("DELETE FROM public.usuario_roles WHERE id_usuario=?", targetId);
            jdbc.update("DELETE FROM public.usuarios WHERE id=?", targetId);
            SecurityContextHolder.clearContext();
        }
    }

    private DriverManagerDataSource dataSource() {
        String url = System.getProperty("admin.rbac.pg.test.url");
        String user = System.getProperty("admin.rbac.pg.test.user", "postgres");
        String password = System.getProperty("admin.rbac.pg.test.password", "");
        assumeTrue(url != null
                && url.startsWith("jdbc:postgresql://127.0.0.1:")
                && url.endsWith("/" + DATABASE_NAME),
                "Set admin.rbac.pg.test.url only to the disposable loopback PostgreSQL database.");
        assumeTrue("postgres".equals(user), "The isolated test role must be postgres.");
        String safeLocalUrl = url + (url.contains("?") ? "&" : "?")
                + "sslmode=disable&connectTimeout=5&socketTimeout=15";
        return new DriverManagerDataSource(safeLocalUrl, user, password);
    }

    private int createAdminUser(JdbcTemplate jdbc, String suffix) {
        String email = "phase-b-" + suffix + "-" + UUID.randomUUID() + "@example.invalid";
        String cpf = String.format("%011d", Long.remainderUnsigned(
                UUID.randomUUID().getMostSignificantBits(), 100_000_000_000L));
        Integer userId = jdbc.queryForObject("""
                INSERT INTO public.usuarios (
                    cpf, data_nascimento, nome, genero, email, telefone, senha_hash,
                    ativo, email_verificado, telefone_verificado, data_criacao
                ) VALUES (?, '1990-01-01', 'Phase B test fixture', 'FEMININO', ?,
                          '5511912345678', 'test-only-hash', TRUE, TRUE, TRUE, now())
                RETURNING id
                """, Integer.class, cpf, email);
        Integer roleId = jdbc.queryForObject("SELECT id FROM public.roles WHERE nome='ADMIN'", Integer.class);
        jdbc.update("INSERT INTO public.usuario_roles (id_usuario,id_role) VALUES (?,?)", userId, roleId);
        return userId;
    }

    private Usuario adminUser(Integer id) {
        Usuario user = new Usuario();
        ReflectionTestUtils.setField(user, "id", id);
        user.setAtivo(true);
        user.setEmail(new Email("phase-b-admin-" + id + "@example.invalid"));
        user.setEmailVerificado(true);
        user.setTelefoneVerificado(true);
        user.setTelefone("5511912345678");
        Role role = new Role();
        role.setNome("ADMIN");
        user.setRoles(new HashSet<>(Set.of(role)));
        return user;
    }

    private void authenticate(Usuario actor) {
        var authentication = new UsernamePasswordAuthenticationToken(
                actor, null, List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ADMIN_SESSION")));
        authentication.setDetails(new AdminAuthenticationDetails(UUID.randomUUID()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
