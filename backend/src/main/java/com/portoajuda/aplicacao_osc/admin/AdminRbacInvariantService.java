package com.portoajuda.aplicacao_osc.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes every application path that can make a Super Admin ineligible. */
@Service
public class AdminRbacInvariantService {

    private final AdminAssignmentRepository assignments;
    private final AdminAuthorizationService authorization;

    public AdminRbacInvariantService(
            AdminAssignmentRepository assignments,
            AdminAuthorizationService authorization) {
        this.assignments = assignments;
        this.authorization = authorization;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockEligibilityChanges() {
        if (authorization.isEnforcementEnabled()) {
            assignments.lockSuperAdminInvariant();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertMayRemoveEligibility(Integer userId) {
        if (!authorization.isEnforcementEnabled()) {
            return;
        }
        if (!canRemoveEligibility(userId)) {
            if (assignments.countActiveSuperAdmins() == 0) {
                throw new IllegalStateException(
                        "RBAC inconsistente: não há SUPER_ADMIN elegível ativo; alteração bloqueada.");
            }
            throw new IllegalStateException(
                    "A operação removeria o último SUPER_ADMIN elegível e ativo.");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean canRemoveEligibility(Integer userId) {
        if (!authorization.isEnforcementEnabled()) {
            return true;
        }
        assignments.lockSuperAdminInvariant();
        long activeCount = assignments.countActiveSuperAdmins();
        long eligibleCount = assignments.countEligibleActiveSuperAdmins();
        boolean activeSuperAdmin = assignments.isActiveSuperAdmin(userId);
        boolean eligibleSuperAdmin = assignments.isEligibleActiveSuperAdmin(userId);
        return activeCount > 0 && eligibleCount > 0
                && !(activeSuperAdmin && activeCount <= 1)
                && !(eligibleSuperAdmin && eligibleCount <= 1);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertAtLeastOneEligibleSuperAdminRemains() {
        if (authorization.isEnforcementEnabled()) {
            assignments.lockSuperAdminInvariant();
            if (assignments.countActiveSuperAdmins() < 1
                    || assignments.countEligibleActiveSuperAdmins() < 1) {
                throw new IllegalStateException(
                        "A operação não pode terminar sem um SUPER_ADMIN elegível e ativo.");
            }
        }
    }
}
