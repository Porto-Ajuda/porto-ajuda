package com.portoajuda.aplicacao_osc.admin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** The single source of truth for profile permissions. Resource scope is checked separately. */
public final class AdminPermissionCatalog {

    private static final Map<AdminProfile, Set<AdminPermission>> PERMISSIONS = buildCatalog();

    private AdminPermissionCatalog() {
    }

    public static Set<AdminPermission> permissionsFor(AdminProfile profile) {
        if (profile == null) {
            return Set.of();
        }
        return PERMISSIONS.get(profile);
    }

    public static boolean grants(AdminProfile profile, AdminPermission permission) {
        return profile != null && permission != null
                && PERMISSIONS.get(profile).contains(permission);
    }

    public static Map<AdminProfile, Set<AdminPermission>> all() {
        return PERMISSIONS;
    }

    private static Map<AdminProfile, Set<AdminPermission>> buildCatalog() {
        EnumMap<AdminProfile, Set<AdminPermission>> result = new EnumMap<>(AdminProfile.class);
        result.put(AdminProfile.SUPER_ADMIN, Set.copyOf(EnumSet.allOf(AdminPermission.class)));
        result.put(AdminProfile.PLATFORM_ADMIN, Set.of(
                AdminPermission.DASHBOARD_READ,
                AdminPermission.ORG_READ,
                AdminPermission.ORG_READ_PRIVATE,
                AdminPermission.ORG_DEACTIVATE,
                AdminPermission.ORG_REACTIVATE,
                AdminPermission.USER_READ,
                AdminPermission.USER_SUSPEND,
                AdminPermission.USER_RESTORE,
                AdminPermission.CONTENT_READ,
                AdminPermission.CONTENT_DEACTIVATE,
                AdminPermission.CONTENT_REACTIVATE,
                AdminPermission.REPORT_READ,
                AdminPermission.REPORT_REVIEW,
                AdminPermission.REPORT_RESOLVE,
                AdminPermission.APPEAL_READ,
                AdminPermission.APPEAL_RESOLVE,
                AdminPermission.AUDIT_READ_CASE,
                AdminPermission.SESSION_REVOKE_SELF));
        result.put(AdminProfile.MODERATOR, Set.of(
                AdminPermission.DASHBOARD_READ,
                AdminPermission.ORG_READ,
                AdminPermission.CONTENT_READ,
                AdminPermission.CONTENT_DEACTIVATE,
                AdminPermission.REPORT_READ,
                AdminPermission.REPORT_REVIEW,
                AdminPermission.AUDIT_READ_CASE,
                AdminPermission.SESSION_REVOKE_SELF));
        result.put(AdminProfile.AUDITOR, Set.of(
                AdminPermission.DASHBOARD_READ,
                AdminPermission.ORG_READ,
                AdminPermission.USER_READ,
                AdminPermission.CONTENT_READ,
                AdminPermission.REPORT_READ,
                AdminPermission.APPEAL_READ,
                AdminPermission.AUDIT_READ_GLOBAL,
                AdminPermission.FINANCE_SUMMARY_READ,
                AdminPermission.SESSION_REVOKE_SELF));
        return Map.copyOf(result);
    }
}
