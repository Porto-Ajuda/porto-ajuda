package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OscDashboardServiceTest {
    private JdbcTemplate jdbc;
    private OscRepository oscs;
    private OscService oscService;
    private OscDashboardService dashboard;
    private Osc osc;
    private Usuario actor;

    @BeforeEach void setUp() {
        jdbc = mock(JdbcTemplate.class); oscs = mock(OscRepository.class); oscService = mock(OscService.class);
        dashboard = new OscDashboardService(jdbc, oscs, oscService);
        osc = new Osc(); ReflectionTestUtils.setField(osc, "id", 42); osc.setNome("ONG exemplo");
        actor = mock(Usuario.class); when(actor.getId()).thenReturn(5);
        when(oscs.findById(42)).thenReturn(Optional.of(osc));
    }

    @Test void editorAndMemberCannotReadDashboardAndNoMetricsAreQueried() {
        for (CargoOsc cargo : new CargoOsc[] { CargoOsc.EDITOR, CargoOsc.MEMBRO }) {
            when(oscService.obterCargoUsuario(osc, actor)).thenReturn(cargo);
            ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> dashboard.dashboard(42, actor));
            assertEquals(403, failure.getStatusCode().value());
        }
        verifyNoInteractions(jdbc);
    }

    @Test void dashboardRejectsAnonymousAndCrossOrganizationScope() {
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> dashboard.dashboard(42, null)).getStatusCode().value());
        Osc other = new Osc(); ReflectionTestUtils.setField(other, "id", 43); other.setNome("Outra ONG");
        when(oscs.findById(43)).thenReturn(Optional.of(other));
        when(oscService.obterCargoUsuario(other, actor)).thenReturn(CargoOsc.MEMBRO);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> dashboard.dashboard(43, actor)).getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test void creatorDashboardReturnsScopedRealMetricGroupsWithoutInventingStripeBalance() {
        when(oscService.obterCargoUsuario(osc, actor)).thenReturn(CargoOsc.CRIADOR);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("cargo_osc")) return List.of(Map.of("label", "CRIADOR", "total", 1L));
            if (sql.contains("SELECT state,count")) return List.of(Map.of("state", "ACTIVE", "total", 1L, "goals", 50000L));
            if (sql.contains("SELECT kind,title")) return List.of(Map.of("kind", "Publicação", "title", "Ação de teste", "occurredAt", java.time.LocalDateTime.now()));
            return List.of();
        }).when(jdbc).queryForList(anyString(), any(Object[].class));
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("publications")) return Map.of("published", 2L, "unpublished", 1L, "volunteerPosts", 1L, "unpublishedVolunteerPosts", 0L);
            if (sql.contains("totalCapacity")) return Map.of("open", 1L, "full", 1L, "completed", 0L, "canceled", 0L, "totalCapacity", 4L, "occupiedSlots", 3L, "availableSlots", 1L);
            if (sql.contains("pendingApplications")) return Map.of("pendingApplications", 2L);
            return Map.of("pending", 2L, "accepted", 3L, "rejected", 1L, "canceled", 0L, "completed", 0L, "canceledByNgo", 0L);
        }).when(jdbc).queryForMap(anyString(), any(Object[].class));
        when(jdbc.queryForObject(contains("to_regclass"), eq(Boolean.class))).thenReturn(true);
        when(jdbc.queryForObject(contains("sum(l.net_delta_cents)"), eq(Long.class), any(Object[].class))).thenReturn(12500L);
        when(jdbc.queryForObject(contains("sum(goal_net_cents)"), eq(Long.class), any(Object[].class))).thenReturn(50000L);

        Map<String,Object> data = dashboard.dashboard(42, actor);

        assertEquals("Desde o início dos registros disponíveis", ((Map<?,?>) data.get("scope")).get("period"));
        assertEquals(1L, ((Map<?,?>) data.get("opportunities")).get("open"));
        assertEquals(1L, ((Map<?,?>) data.get("opportunities")).get("full"));
        assertEquals(1L, ((Map<?,?>) data.get("opportunities")).get("availableSlots"));
        assertEquals(12500L, ((Map<?,?>) data.get("campaigns")).get("confirmedNetRaisedCents"));
        assertFalse((Boolean) ((Map<?,?>) data.get("stripeBalance")).get("available"));
        assertEquals("Indisponível", ((Map<?,?>) data.get("stripeBalance")).get("label"));
    }
}
