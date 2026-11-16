package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.admin.AdminProfile;
import com.portoajuda.aplicacao_osc.dto.request.RequestContentReportDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestContentReportDecisionDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.ContentReportJdbcRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ContentReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    @Mock ContentReportJdbcRepository reports;
    @Mock OscRepository oscs;
    @Mock PostRepository posts;
    @Mock UsuarioRepository users;
    @Mock AutenticacaoAbusoUsuarioService abuse;
    @Mock AdminAuthCodeSecurity identifierSecurity;
    @Mock AdminAuthorizationService admin;
    @Mock ApplicationEventPublisher events;
    @Mock Usuario reporter;
    @Mock Osc osc;
    @Mock Post post;
    @Mock Vaga vaga;

    ContentReportService service;

    @Test void omittedVisibilityChoiceDefaultsToNoNewRestrictionForCompatibility() {
        RequestContentReportDecisionDTO request = new RequestContentReportDecisionDTO(
                RequestContentReportDecisionDTO.Decisao.DISMISS, null, null, "Legacy compatible justification.");
        assertEquals(false, request.restringirVisibilidade());
    }

    @BeforeEach void setup() {
        service = serviceWithClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void reportRequiresNonBlankDescriptionAndEnforcesTheExistingLimitAtServiceBoundary() {
        when(reporter.getId()).thenReturn(501);
        when(reporter.isEnabled()).thenReturn(true);
        when(reporter.isEmailVerificado()).thenReturn(true);
        for (String description : new String[] { null, "", " \t\n ", "x".repeat(1001) }) {
            RequestContentReportDTO request = new RequestContentReportDTO(RequestContentReportDTO.TipoAlvo.OSC,
                    700, RequestContentReportDTO.Motivo.OTHER, description);
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.reportar(request, reporter, "127.0.0.1"));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        verifyNoInteractions(reports, abuse, oscs, posts);
    }

    @Test void volunteerReportUsesCanonicalPostAndActivatesAtConfiguredThreshold() {
        when(identifierSecurity.hashIdentifier(anyString(), anyString())).thenReturn("a".repeat(64));
        when(reporter.getId()).thenReturn(501);
        when(reporter.isEnabled()).thenReturn(true);
        when(reporter.isEmailVerificado()).thenReturn(true);
        when(posts.findById(900)).thenReturn(Optional.of(post));
        when(post.getOsc()).thenReturn(osc);
        when(osc.getId()).thenReturn(700);
        when(oscs.findForUpdateById(700)).thenReturn(Optional.of(osc));
        when(osc.isArquivada()).thenReturn(false);
        when(osc.getStatus()).thenReturn(StatusOsc.ATIVA);
        when(posts.findForUpdateById(900)).thenReturn(Optional.of(post));
        when(post.isAtivo()).thenReturn(true);
        when(post.getTipo()).thenReturn(TipoPost.VOLUNTARIADO);
        when(post.getVagaVoluntariado()).thenReturn(vaga);
        when(vaga.getStatus()).thenReturn(StatusVaga.ABERTA);
        when(vaga.getQuantidadeVagas()).thenReturn(2);
        when(vaga.getDataFim()).thenReturn(LocalDate.now().plusDays(4));
        when(abuse.permitirDenuncia(501, "127.0.0.1")).thenReturn(true);
        when(reports.insert("POST", 900, "VOLUNTEERING", "a".repeat(64), "FRAUD", "Informação suspeita")).thenReturn(true);
        when(reports.qualifyingCount("POST", 900, "VOLUNTEERING", 24)).thenReturn(5L);
        when(reports.activateAutomaticProtection("POST", 900, "VOLUNTEERING")).thenReturn(1);

        var response = service.reportar(new RequestContentReportDTO(
                RequestContentReportDTO.TipoAlvo.VOLUNTARIADO, 900,
                RequestContentReportDTO.Motivo.FRAUD, "Informação suspeita"), reporter, "127.0.0.1");

        assertEquals("PENDING", response.status());
        verify(reports).insert("POST", 900, "VOLUNTEERING", "a".repeat(64), "FRAUD", "Informação suspeita");
        verify(reports).activateAutomaticProtection("POST", 900, "VOLUNTEERING");
    }

    @Test void duplicateReportIsAControlledConflict() {
        when(identifierSecurity.hashIdentifier(anyString(), anyString())).thenReturn("a".repeat(64));
        preparePublicOrganization();
        when(abuse.permitirDenuncia(501, "127.0.0.1")).thenReturn(true);
        when(reports.insert("OSC", 700, "OSC", "a".repeat(64), "OTHER", "Justificativa válida")).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.reportar(
                new RequestContentReportDTO(RequestContentReportDTO.TipoAlvo.OSC, 700,
                        RequestContentReportDTO.Motivo.OTHER, "Justificativa válida"), reporter, "127.0.0.1"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(reports, never()).activateAutomaticProtection("OSC", 700, "OSC");
    }

    @Test void anExistingPendingReportReturnsTheSpecificConflictWithoutAttemptingInsert() {
        when(identifierSecurity.hashIdentifier(anyString(), anyString())).thenReturn("a".repeat(64));
        preparePublicOrganization();
        when(abuse.permitirDenuncia(501, "127.0.0.1")).thenReturn(true);
        when(reports.hasPendingReport("OSC", 700, "a".repeat(64))).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.reportar(
                new RequestContentReportDTO(RequestContentReportDTO.TipoAlvo.OSC, 700,
                        RequestContentReportDTO.Motivo.OTHER, "Justificativa válida"), reporter, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("pendente"));
        verify(reports, never()).latestReviewedAt("OSC", 700, "a".repeat(64));
        verify(reports, never()).insert("OSC", 700, "OSC", "a".repeat(64), "OTHER", "Justificativa válida");
    }

    @Test void cooldownRejectsOneInstantBeforeSevenDaysAndAllowsTheExactBoundary() {
        String key = "a".repeat(64);
        when(identifierSecurity.hashIdentifier(anyString(), anyString())).thenReturn(key);
        preparePublicOrganization();
        when(abuse.permitirDenuncia(501, "127.0.0.1")).thenReturn(true);
        when(reports.latestReviewedAt("OSC", 700, key)).thenReturn(NOW.minus(Duration.ofDays(7)).plusNanos(1));

        ResponseStatusException beforeBoundary = assertThrows(ResponseStatusException.class, () -> service.reportar(
                new RequestContentReportDTO(RequestContentReportDTO.TipoAlvo.OSC, 700,
                        RequestContentReportDTO.Motivo.OTHER, "Justificativa válida"), reporter, "127.0.0.1"));
        assertEquals(HttpStatus.CONFLICT, beforeBoundary.getStatusCode());
        assertTrue(beforeBoundary.getReason().contains("7 dias"));
        verify(reports, never()).insert("OSC", 700, "OSC", key, "OTHER", "");

        service = serviceWithClock(Clock.fixed(NOW, ZoneOffset.UTC));
        preparePublicOrganization();
        when(abuse.permitirDenuncia(501, "127.0.0.1")).thenReturn(true);
        when(reports.latestReviewedAt("OSC", 700, key)).thenReturn(NOW.minus(Duration.ofDays(7)));
        when(reports.insert("OSC", 700, "OSC", key, "OTHER", "Justificativa válida")).thenReturn(true);

        var response = service.reportar(new RequestContentReportDTO(
                RequestContentReportDTO.TipoAlvo.OSC, 700, RequestContentReportDTO.Motivo.OTHER, "Justificativa válida"),
                reporter, "127.0.0.1");
        assertEquals("PENDING", response.status());
        verify(reports).insert("OSC", 700, "OSC", key, "OTHER", "Justificativa válida");
    }

    @Test void unverifiedReporterIsRejectedBeforeTargetLookup() {
        when(reporter.getId()).thenReturn(501);
        when(reporter.isEnabled()).thenReturn(true);
        when(reporter.isEmailVerificado()).thenReturn(false);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.reportar(
                new RequestContentReportDTO(RequestContentReportDTO.TipoAlvo.OSC, 700,
                        RequestContentReportDTO.Motivo.OTHER, "Justificativa válida"), reporter, "127.0.0.1"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(oscs, never()).findForUpdateById(eq(700));
    }

    @Test void inactiveOrganizationIsNotReportableThroughThePublicRoute() {
        when(reporter.getId()).thenReturn(501);
        when(reporter.isEnabled()).thenReturn(true);
        when(reporter.isEmailVerificado()).thenReturn(true);
        when(oscs.findForUpdateById(700)).thenReturn(Optional.of(osc));
        when(osc.isArquivada()).thenReturn(false);
        when(osc.getStatus()).thenReturn(StatusOsc.INATIVA);
        when(abuse.permitirDenuncia(501, "127.0.0.1")).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.reportar(
                new RequestContentReportDTO(RequestContentReportDTO.TipoAlvo.OSC, 700,
                        RequestContentReportDTO.Motivo.OTHER, "Justificativa válida"), reporter, "127.0.0.1"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(reports, never()).insert("OSC", 700, "OSC", "a".repeat(64), "OTHER", "");
    }

    @Test void rateLimitStopsInsertionWithoutLockingTheAccount() {
        when(reporter.getId()).thenReturn(501);
        when(reporter.isEnabled()).thenReturn(true);
        when(reporter.isEmailVerificado()).thenReturn(true);
        when(abuse.permitirDenuncia(501, "127.0.0.1")).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.reportar(
                new RequestContentReportDTO(RequestContentReportDTO.TipoAlvo.OSC, 700,
                        RequestContentReportDTO.Motivo.OTHER, "Justificativa válida"), reporter, "127.0.0.1"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        verify(reports, never()).insert("OSC", 700, "OSC", "a".repeat(64), "OTHER", "");
        verify(oscs, never()).findForUpdateById(700);
    }

    @Test void upheldReportSeparatesPointDecisionFromVisibilityRestriction() {
        when(oscs.findForUpdateById(700)).thenReturn(Optional.of(osc));
        AdminActor actor = new AdminActor(900, UUID.randomUUID(), AdminProfile.PLATFORM_ADMIN);
        when(admin.requirePermission(AdminPermission.REPORT_REVIEW)).thenReturn(actor);
        when(admin.requirePermission(AdminPermission.REPORT_RESOLVE)).thenReturn(actor);
        when(reports.decide("OSC", 700, "UPHOLD", "Valid upheld justification.", false, actor, NOW))
                .thenReturn(41L);

        service.decidir("OSC", 700, new RequestContentReportDecisionDTO(
                RequestContentReportDecisionDTO.Decisao.UPHOLD, 2, false, "Valid upheld justification."));

        verify(reports).decide("OSC", 700, "UPHOLD", "Valid upheld justification.", false, actor, NOW);
        verify(admin, never()).requirePermission(AdminPermission.CONTENT_DEACTIVATE);
    }

    @Test void upheldReportAddsVisibilityOnlyWhenTheAdministratorExplicitlyChoosesIt() {
        when(oscs.findForUpdateById(700)).thenReturn(Optional.of(osc));
        AdminActor actor = new AdminActor(900, UUID.randomUUID(), AdminProfile.PLATFORM_ADMIN);
        when(admin.requirePermission(AdminPermission.REPORT_REVIEW)).thenReturn(actor);
        when(admin.requirePermission(AdminPermission.REPORT_RESOLVE)).thenReturn(actor);
        when(admin.requirePermission(AdminPermission.CONTENT_DEACTIVATE)).thenReturn(actor);
        when(reports.decide("OSC", 700, "UPHOLD", "Valid restriction justification.", true, actor, NOW))
                .thenReturn(42L);

        service.decidir("OSC", 700, new RequestContentReportDecisionDTO(
                RequestContentReportDecisionDTO.Decisao.UPHOLD, 1, true, "Valid restriction justification."));

        verify(reports).decide("OSC", 700, "UPHOLD", "Valid restriction justification.", true, actor, NOW);
        verify(admin).requirePermission(AdminPermission.CONTENT_DEACTIVATE);
    }

    @Test void preventiveHideRequiresPendingReportsAndDoesNotResolveOrScoreTheTarget() {
        when(oscs.findForUpdateById(700)).thenReturn(Optional.of(osc));
        AdminActor actor = new AdminActor(900, UUID.randomUUID(), AdminProfile.MODERATOR);
        when(admin.requirePermission(AdminPermission.REPORT_REVIEW)).thenReturn(actor);
        when(admin.requirePermission(AdminPermission.CONTENT_DEACTIVATE)).thenReturn(actor);
        when(reports.hasPendingReports("OSC", 700)).thenReturn(true);
        when(reports.pendingContentKind("OSC", 700)).thenReturn("OSC");
        when(reports.createPreventiveRestriction("OSC", 700, "OSC", "Synthetic pending hide reason.", actor))
                .thenReturn(1);

        service.ocultarEnquantoPendente("OSC", 700, "Synthetic pending hide reason.");

        verify(reports).createPreventiveRestriction("OSC", 700, "OSC", "Synthetic pending hide reason.", actor);
        verify(reports, never()).decide(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test void explicitRestoreTargetsOneBlockAndDoesNotUseReportResolution() {
        when(oscs.findForUpdateById(700)).thenReturn(Optional.of(osc));
        AdminActor actor = new AdminActor(900, UUID.randomUUID(), AdminProfile.PLATFORM_ADMIN);
        when(admin.requirePermission(AdminPermission.CONTENT_REACTIVATE)).thenReturn(actor);
        when(reports.restoreRestriction("OSC", 700, 81L, "Synthetic restore justification.", actor)).thenReturn(true);

        service.restaurarRestricao("OSC", 700, 81L, "Synthetic restore justification.");

        verify(reports).restoreRestriction("OSC", 700, 81L, "Synthetic restore justification.", actor);
        verify(reports, never()).decide(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test void featureDisabledReturnsBeforeQueryingReportingTables() {
        ContentReportService disabled = new ContentReportService(reports, oscs, posts, users, abuse,
                identifierSecurity, admin, events, false, 10, 5, 5, 48, 24, 24);
        assertEquals(false, disabled.isBlocked("POST", 900));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> disabled.reportar(null, null, null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verifyNoInteractions(reports);
    }

    private void preparePublicOrganization() {
        when(reporter.getId()).thenReturn(501);
        when(reporter.isEnabled()).thenReturn(true);
        when(reporter.isEmailVerificado()).thenReturn(true);
        when(oscs.findForUpdateById(700)).thenReturn(Optional.of(osc));
        when(osc.isArquivada()).thenReturn(false);
        when(osc.getStatus()).thenReturn(StatusOsc.ATIVA);
    }

    private ContentReportService serviceWithClock(Clock clock) {
        return new ContentReportService(reports, oscs, posts, users, abuse, identifierSecurity, admin, events,
                true, 10, 5, 5, 48, 24, 24, clock);
    }
}
