package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.entity.AdminSecurityAuditEvent;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.repository.AdminSecurityAuditEventRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.PostRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private OscRepository oscRepository;
    @Mock private PostRepository postRepository;
    @Mock private VagaRepository vagaRepository;
    @Mock private AdminSecurityAuditEventRepository auditRepository;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(
                usuarioRepository, oscRepository, postRepository, vagaRepository, auditRepository);
    }

    @Test
    void contaAgregaMetricasReaisEDevolveSomenteDescricaoSanitizadaDaAuditoria() {
        when(usuarioRepository.count()).thenReturn(80L);
        when(oscRepository.countByStatusAndDataArquivamentoIsNull(StatusOsc.ATIVA)).thenReturn(12L);
        when(oscRepository.countByStatusAndDataArquivamentoIsNull(StatusOsc.INATIVA)).thenReturn(5L);
        when(oscRepository.countByDataArquivamentoIsNotNull()).thenReturn(3L);
        when(postRepository.count()).thenReturn(27L);
        when(vagaRepository.count()).thenReturn(8L);
        when(auditRepository.findTop10ByEventTypeStartingWithOrderByOccurredAtDesc("ADMIN_"))
                .thenReturn(List.of(evento("ADMIN_SESSION_ISSUED", Instant.parse("2026-11-06T12:00:00Z"))));

        var overview = service.obterVisaoGeral();

        assertEquals(80L, overview.registeredUsers());
        assertEquals(12L, overview.activeOrganizations());
        assertEquals(5L, overview.inactiveOrganizations());
        assertEquals(3L, overview.archivedOrganizations());
        assertEquals(27L, overview.publications());
        assertEquals(8L, overview.volunteerOpportunities());
        assertTrue(overview.recentActivityAvailable());
        assertEquals("Sessão administrativa iniciada", overview.recentActivity().getFirst().description());
        assertNotNull(overview.generatedAt());
        verify(auditRepository).findTop10ByEventTypeStartingWithOrderByOccurredAtDesc("ADMIN_");
    }

    @Test
    void conjuntoVazioRetornaZerosReaisEListaDeAtividadeVazia() {
        when(usuarioRepository.count()).thenReturn(0L);
        when(oscRepository.countByStatusAndDataArquivamentoIsNull(StatusOsc.ATIVA)).thenReturn(0L);
        when(oscRepository.countByStatusAndDataArquivamentoIsNull(StatusOsc.INATIVA)).thenReturn(0L);
        when(oscRepository.countByDataArquivamentoIsNotNull()).thenReturn(0L);
        when(postRepository.count()).thenReturn(0L);
        when(vagaRepository.count()).thenReturn(0L);
        when(auditRepository.findTop10ByEventTypeStartingWithOrderByOccurredAtDesc("ADMIN_"))
                .thenReturn(List.of());

        var overview = service.obterVisaoGeral();

        assertEquals(0L, overview.registeredUsers());
        assertEquals(0L, overview.activeOrganizations());
        assertEquals(0L, overview.inactiveOrganizations());
        assertEquals(0L, overview.archivedOrganizations());
        assertEquals(0L, overview.publications());
        assertEquals(0L, overview.volunteerOpportunities());
        assertTrue(overview.recentActivityAvailable());
        assertTrue(overview.recentActivity().isEmpty());
    }

    @Test
    void indisponibilidadeDaTrilhaNaoInventaAtividadeNemDescartaIndicadores() {
        when(usuarioRepository.count()).thenReturn(2L);
        when(oscRepository.countByStatusAndDataArquivamentoIsNull(StatusOsc.ATIVA)).thenReturn(1L);
        when(oscRepository.countByStatusAndDataArquivamentoIsNull(StatusOsc.INATIVA)).thenReturn(0L);
        when(oscRepository.countByDataArquivamentoIsNotNull()).thenReturn(0L);
        when(postRepository.count()).thenReturn(4L);
        when(vagaRepository.count()).thenReturn(1L);
        when(auditRepository.findTop10ByEventTypeStartingWithOrderByOccurredAtDesc("ADMIN_"))
                .thenThrow(new DataAccessResourceFailureException("private database detail"));

        var overview = service.obterVisaoGeral();

        assertEquals(2L, overview.registeredUsers());
        assertFalse(overview.recentActivityAvailable());
        assertTrue(overview.recentActivity().isEmpty());
    }

    @Test
    void indisponibilidadeDosIndicadoresRetornaErroServicoGenerico() {
        when(usuarioRepository.count())
                .thenThrow(new DataAccessResourceFailureException("sensitive schema detail"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, service::obterVisaoGeral);

        assertEquals(503, error.getStatusCode().value());
        assertFalse(error.getReason().contains("sensitive schema detail"));
    }

    private AdminSecurityAuditEvent evento(String type, Instant occurredAt) {
        AdminSecurityAuditEvent event = new AdminSecurityAuditEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(type);
        event.setUsuarioIdSnapshot(991);
        event.setChallengeId(UUID.randomUUID());
        event.setAccountKeyHash("account-key-hash-must-not-be-returned");
        event.setIpKeyHash("ip-key-hash-must-not-be-returned");
        event.setOccurredAt(occurredAt);
        return event;
    }
}
