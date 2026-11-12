package com.portoajuda.aplicacao_osc.admin;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.utils.Email;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AdminAssignmentServiceTest {

    private final AdminAssignmentRepository assignments = mock(AdminAssignmentRepository.class);
    private final AdminOperationAuditService audit = mock(AdminOperationAuditService.class);
    private final AdminPrivilegedSessionService sessions = mock(AdminPrivilegedSessionService.class);
    private final UsuarioRepository users = mock(UsuarioRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final AdminAuthorizationService authorization = new AdminAuthorizationService(assignments, true);
    private final AdminRbacInvariantService invariant = new AdminRbacInvariantService(assignments, authorization);
    private final AdminAssignmentService service = new AdminAssignmentService(
            assignments, authorization, invariant, audit, sessions, users, roles);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminConcedePerfilComAuditoriaESessaoRevogada() {
        authenticateAsSuperAdmin();
        Usuario target = adminUser(55);
        when(assignments.findActiveByUserId(44)).thenReturn(Optional.of(assignment(44, AdminProfile.SUPER_ADMIN)));
        when(users.findByIdForUpdate(55)).thenReturn(Optional.of(target));
        when(assignments.findActiveByUserIdForUpdate(55)).thenReturn(Optional.empty());

        service.assignOrChange(new AdminAssignmentRequest(55, AdminProfile.MODERATOR, "ticket-123"));

        verify(assignments).insert(any(AdminAssignment.class));
        verify(sessions).revogarTodasDoUsuario(55, "ADMIN_REVOKED");
        verify(audit).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void impedeAutopromocaoOuAutogerenciaDeAtribuicao() {
        authenticateAsSuperAdmin();
        when(assignments.findActiveByUserId(44)).thenReturn(Optional.of(assignment(44, AdminProfile.SUPER_ADMIN)));

        assertThrows(AccessDeniedException.class,
                () -> service.assignOrChange(new AdminAssignmentRequest(44, AdminProfile.SUPER_ADMIN, "self")));

        verify(assignments, never()).insert(any());
    }

    @Test
    void naoConcedePerfilAContaComTelefoneVerificadoMasIncompativelComMfa() {
        authenticateAsSuperAdmin();
        Usuario target = adminUser(55);
        target.setTelefone("not-a-brazilian-mobile");
        when(assignments.findActiveByUserId(44)).thenReturn(Optional.of(assignment(44, AdminProfile.SUPER_ADMIN)));
        when(users.findByIdForUpdate(55)).thenReturn(Optional.of(target));

        assertThrows(ResponseStatusException.class,
                () -> service.assignOrChange(new AdminAssignmentRequest(55, AdminProfile.MODERATOR, "PA-3001")));

        verify(assignments, never()).insert(any());
        verify(sessions, never()).revogarTodasDoUsuario(55, "ADMIN_REVOKED");
    }

    @Test
    void impedeDemocaoConcorrenteDoUltimoSuperAdminAtivo() {
        authenticateAsSuperAdmin();
        UUID assignmentId = UUID.randomUUID();
        AdminAssignment targetAssignment = new AdminAssignment(
                assignmentId, 55, 55, AdminProfile.SUPER_ADMIN, AdminAssignmentStatus.ACTIVE,
                Instant.now(), 44, Instant.now(), 44, null, "ticket-previous");
        when(assignments.findActiveByUserId(44)).thenReturn(Optional.of(assignment(44, AdminProfile.SUPER_ADMIN)));
        when(assignments.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(targetAssignment));
        when(users.findByIdForUpdate(55)).thenReturn(Optional.of(adminUser(55)));
        when(assignments.countActiveSuperAdmins()).thenReturn(1L);
        when(assignments.countEligibleActiveSuperAdmins()).thenReturn(1L);
        when(assignments.isActiveSuperAdmin(55)).thenReturn(true);
        when(assignments.isEligibleActiveSuperAdmin(55)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.changeProfile(assignmentId,
                        new AdminAssignmentProfileRequest(AdminProfile.PLATFORM_ADMIN, "ticket-demote")));

        verify(assignments, never()).revoke(any(), any(), any(), any());
    }

    private void authenticateAsSuperAdmin() {
        Usuario actor = adminUser(44);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                actor, null, List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ADMIN_SESSION")));
        authentication.setDetails(new AdminAuthenticationDetails(UUID.randomUUID()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Usuario adminUser(int id) {
        Usuario user = new Usuario();
        ReflectionTestUtils.setField(user, "id", id);
        user.setAtivo(true);
        user.setEmail(new Email("admin" + id + "@example.invalid"));
        user.setEmailVerificado(true);
        user.setTelefoneVerificado(true);
        user.setTelefone("5511912345678");
        Role role = new Role();
        role.setNome("ADMIN");
        user.setRoles(new HashSet<>(Set.of(role)));
        return user;
    }

    private AdminAssignment assignment(int userId, AdminProfile profile) {
        Instant now = Instant.now();
        return new AdminAssignment(UUID.randomUUID(), userId, userId, profile,
                AdminAssignmentStatus.ACTIVE, now, 1, now, 1, null, "approved");
    }
}
