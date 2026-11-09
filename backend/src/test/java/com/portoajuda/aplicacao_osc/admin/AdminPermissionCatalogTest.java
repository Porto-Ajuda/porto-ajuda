package com.portoajuda.aplicacao_osc.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminPermissionCatalogTest {

    @Test
    void superAdminRecebeCatalogoCompleto() {
        for (AdminPermission permission : AdminPermission.values()) {
            assertTrue(AdminPermissionCatalog.grants(AdminProfile.SUPER_ADMIN, permission));
        }
    }

    @Test
    void platformAdminNaoConsegueGerirAtribuicoesOuConfiguracao() {
        assertTrue(AdminPermissionCatalog.grants(AdminProfile.PLATFORM_ADMIN, AdminPermission.ORG_DEACTIVATE));
        assertTrue(AdminPermissionCatalog.grants(AdminProfile.PLATFORM_ADMIN, AdminPermission.CONTENT_REACTIVATE));
        assertTrue(AdminPermissionCatalog.grants(AdminProfile.PLATFORM_ADMIN, AdminPermission.APPEAL_RESOLVE));
        assertFalse(AdminPermissionCatalog.grants(AdminProfile.PLATFORM_ADMIN, AdminPermission.ADMIN_MANAGE));
        assertFalse(AdminPermissionCatalog.grants(AdminProfile.PLATFORM_ADMIN, AdminPermission.SETTINGS_MANAGE));
    }

    @Test
    void moderatorInvestigaEDesativaMasNaoReativaNemAdministraContas() {
        assertTrue(AdminPermissionCatalog.grants(AdminProfile.MODERATOR, AdminPermission.CONTENT_DEACTIVATE));
        assertTrue(AdminPermissionCatalog.grants(AdminProfile.MODERATOR, AdminPermission.REPORT_REVIEW));
        assertFalse(AdminPermissionCatalog.grants(AdminProfile.MODERATOR, AdminPermission.CONTENT_REACTIVATE));
        assertFalse(AdminPermissionCatalog.grants(AdminProfile.MODERATOR, AdminPermission.ORG_RESTORE_ARCHIVED));
        assertFalse(AdminPermissionCatalog.grants(AdminProfile.MODERATOR, AdminPermission.ORG_DEACTIVATE));
        assertFalse(AdminPermissionCatalog.grants(AdminProfile.MODERATOR, AdminPermission.USER_SUSPEND));
        assertFalse(AdminPermissionCatalog.grants(AdminProfile.MODERATOR, AdminPermission.REPORT_RESOLVE));
        assertFalse(AdminPermissionCatalog.grants(AdminProfile.MODERATOR, AdminPermission.ADMIN_MANAGE));
    }

    @Test
    void auditorTemApenasLeitura() {
        assertTrue(AdminPermissionCatalog.grants(AdminProfile.AUDITOR, AdminPermission.AUDIT_READ_GLOBAL));
        assertTrue(AdminPermissionCatalog.grants(AdminProfile.AUDITOR, AdminPermission.FINANCE_SUMMARY_READ));
        for (AdminPermission permission : java.util.List.of(
                AdminPermission.ORG_DEACTIVATE, AdminPermission.ORG_REACTIVATE,
                AdminPermission.ORG_RESTORE_ARCHIVED, AdminPermission.ORG_ARCHIVE,
                AdminPermission.USER_SUSPEND, AdminPermission.USER_RESTORE,
                AdminPermission.CONTENT_PUBLISH, AdminPermission.CONTENT_EDIT_NEWS,
                AdminPermission.CONTENT_DELETE, AdminPermission.CONTENT_DEACTIVATE,
                AdminPermission.CONTENT_REACTIVATE, AdminPermission.REPORT_REVIEW,
                AdminPermission.REPORT_RESOLVE, AdminPermission.APPEAL_RESOLVE,
                AdminPermission.AUDIT_READ_CASE, AdminPermission.ADMIN_MANAGE,
                AdminPermission.SETTINGS_MANAGE)) {
            assertFalse(AdminPermissionCatalog.grants(AdminProfile.AUDITOR, permission), permission.name());
        }
    }
}
