package com.portoajuda.aplicacao_osc.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AdminRbacInvariantServiceTest {

    @Test
    void lockGlobalPrecedeContagemEImpedirRetiradaDoUltimoSuperAdmin() {
        AdminAssignmentRepository assignments = mock(AdminAssignmentRepository.class);
        AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
        when(authorization.isEnforcementEnabled()).thenReturn(true);
        when(assignments.countActiveSuperAdmins()).thenReturn(1L);
        when(assignments.countEligibleActiveSuperAdmins()).thenReturn(1L);
        when(assignments.isActiveSuperAdmin(12)).thenReturn(true);
        when(assignments.isEligibleActiveSuperAdmin(12)).thenReturn(true);
        AdminRbacInvariantService invariant = new AdminRbacInvariantService(assignments, authorization);

        assertFalse(invariant.canRemoveEligibility(12));
        InOrder order = inOrder(assignments);
        order.verify(assignments).lockSuperAdminInvariant();
        order.verify(assignments).countActiveSuperAdmins();
        order.verify(assignments).countEligibleActiveSuperAdmins();
        order.verify(assignments).isActiveSuperAdmin(12);
        order.verify(assignments).isEligibleActiveSuperAdmin(12);
    }

    @Test
    void permiteRetirarUmSuperAdminQuandoOutroPermaneceAtivo() {
        AdminAssignmentRepository assignments = mock(AdminAssignmentRepository.class);
        AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
        when(authorization.isEnforcementEnabled()).thenReturn(true);
        when(assignments.countActiveSuperAdmins()).thenReturn(2L);
        when(assignments.countEligibleActiveSuperAdmins()).thenReturn(2L);
        when(assignments.isActiveSuperAdmin(12)).thenReturn(true);
        when(assignments.isEligibleActiveSuperAdmin(12)).thenReturn(true);
        AdminRbacInvariantService invariant = new AdminRbacInvariantService(assignments, authorization);

        assertTrue(invariant.canRemoveEligibility(12));
    }

    @Test
    void bloqueiaEstadoInconsistenteSemNenhumSuperAdminAtivo() {
        AdminAssignmentRepository assignments = mock(AdminAssignmentRepository.class);
        AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
        when(authorization.isEnforcementEnabled()).thenReturn(true);
        when(assignments.countActiveSuperAdmins()).thenReturn(0L);
        when(assignments.countEligibleActiveSuperAdmins()).thenReturn(0L);
        when(assignments.isActiveSuperAdmin(12)).thenReturn(false);
        when(assignments.isEligibleActiveSuperAdmin(12)).thenReturn(false);
        AdminRbacInvariantService invariant = new AdminRbacInvariantService(assignments, authorization);

        assertFalse(invariant.canRemoveEligibility(12));
        assertThrows(IllegalStateException.class, () -> invariant.assertMayRemoveEligibility(12));
    }

    @Test
    void naoPermiteRemoverUltimoSuperElegivelMesmoSeOutroEstaSemMfaElegivel() {
        AdminAssignmentRepository assignments = mock(AdminAssignmentRepository.class);
        AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
        when(authorization.isEnforcementEnabled()).thenReturn(true);
        when(assignments.countActiveSuperAdmins()).thenReturn(2L);
        when(assignments.countEligibleActiveSuperAdmins()).thenReturn(1L);
        when(assignments.isActiveSuperAdmin(12)).thenReturn(true);
        when(assignments.isEligibleActiveSuperAdmin(12)).thenReturn(true);
        AdminRbacInvariantService invariant = new AdminRbacInvariantService(assignments, authorization);

        assertFalse(invariant.canRemoveEligibility(12));
    }
}
