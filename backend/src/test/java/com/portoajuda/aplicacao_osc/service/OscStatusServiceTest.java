package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestAtualizarStatusOscDTO;
import com.portoajuda.aplicacao_osc.admin.AdminAssignmentRepository;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminOperationAuditService;
import com.portoajuda.aplicacao_osc.entity.HistoricoStatusOsc;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.OrigemDesativacao;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.TipoEventoHistoricoOsc;
import com.portoajuda.aplicacao_osc.repository.HistoricoStatusOscRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OscStatusServiceTest {

    private static final int OSC_ID = 77;

    @Mock private OscRepository oscRepository;
    @Mock private HistoricoStatusOscRepository historicoRepository;
    @Mock private OscService oscService;
    @Mock private AdminAssignmentRepository assignmentRepository;
    @Mock private AdminOperationAuditService operationAudit;

    private OscStatusService service;
    private Osc osc;
    private Usuario criador;

    @BeforeEach
    void configurar() {
        service = new OscStatusService(oscRepository, historicoRepository, oscService,
                new AdminAuthorizationService(assignmentRepository, false), operationAudit);
        osc = new Osc();
        ReflectionTestUtils.setField(osc, "id", OSC_ID);
        osc.setStatus(StatusOsc.INATIVA);
        criador = usuario(10);
        lenient().when(oscRepository.findForUpdateById(OSC_ID)).thenReturn(Optional.of(osc));
        lenient().when(oscService.obterCargoUsuario(osc, criador)).thenReturn(CargoOsc.CRIADOR);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criadorNaoReativaOngArquivadaComOrigemPortoAjuda() {
        osc.setOrigemUltimaDesativacao(OrigemDesativacao.PORTO_AJUDA);
        osc.setDataArquivamento(LocalDateTime.now());

        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.atualizarPorOrganizacao(OSC_ID,
                        new RequestAtualizarStatusOscDTO(StatusOsc.ATIVA, null), criador));

        assertEquals(403, erro.getStatusCode().value());
        assertEquals(StatusOsc.INATIVA, osc.getStatus());
        assertNotNull(osc.getDataArquivamento());
        verify(oscRepository, never()).save(osc);
        verifyNoInteractions(historicoRepository);
    }

    @Test
    void criadorNaoReativaOrigemIndeterminadaMesmoDepoisDoArquivamento() {
        osc.setOrigemUltimaDesativacao(OrigemDesativacao.INDETERMINADA);
        osc.setDataArquivamento(LocalDateTime.now());

        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.atualizarPorOrganizacao(OSC_ID,
                        new RequestAtualizarStatusOscDTO(StatusOsc.ATIVA, null), criador));

        assertEquals(403, erro.getStatusCode().value());
        verify(oscRepository, never()).save(osc);
        verifyNoInteractions(historicoRepository);
    }

    @Test
    void criadorPodeReativarOrigemOng() {
        osc.setOrigemUltimaDesativacao(OrigemDesativacao.ONG);
        osc.setDataUltimaDesativacao(LocalDateTime.now().minusHours(2));

        service.atualizarPorOrganizacao(OSC_ID,
                new RequestAtualizarStatusOscDTO(StatusOsc.ATIVA, null), criador);

        assertEquals(StatusOsc.ATIVA, osc.getStatus());
        assertEquals(OrigemDesativacao.ONG, osc.getOrigemUltimaDesativacao());
        ArgumentCaptor<HistoricoStatusOsc> historico = ArgumentCaptor.forClass(HistoricoStatusOsc.class);
        verify(historicoRepository).save(historico.capture());
        assertEquals(TipoEventoHistoricoOsc.TRANSICAO_STATUS, historico.getValue().getTipoEvento());
        assertEquals(OrigemDesativacao.ONG, historico.getValue().getOrigemDesativacaoAnterior());
    }

    @Test
    void criadorPodeRestaurarOngArquivadaComOrigemOng() {
        osc.setOrigemUltimaDesativacao(OrigemDesativacao.ONG);
        osc.setDataArquivamento(LocalDateTime.now().minusDays(1));
        osc.setMotivoArquivamento("Encerramento anterior da organização");

        service.atualizarPorOrganizacao(OSC_ID,
                new RequestAtualizarStatusOscDTO(StatusOsc.ATIVA, "Retomada das atividades"), criador);

        assertEquals(StatusOsc.ATIVA, osc.getStatus());
        assertNull(osc.getDataArquivamento());
        ArgumentCaptor<HistoricoStatusOsc> historico = ArgumentCaptor.forClass(HistoricoStatusOsc.class);
        verify(historicoRepository).save(historico.capture());
        assertEquals(TipoEventoHistoricoOsc.RESTAURACAO, historico.getValue().getTipoEvento());
        assertEquals(OrigemDesativacao.ONG, historico.getValue().getOrigemDesativacaoAnterior());
    }

    @Test
    void editorNaoPodeAlterarStatusNoBackend() {
        when(oscService.obterCargoUsuario(osc, criador)).thenReturn(CargoOsc.EDITOR);

        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.atualizarPorOrganizacao(OSC_ID,
                        new RequestAtualizarStatusOscDTO(StatusOsc.ATIVA, null), criador));

        assertEquals(403, erro.getStatusCode().value());
        verify(oscRepository, never()).save(osc);
        verifyNoInteractions(historicoRepository);
    }

    @Test
    void roleAdminPodeRestaurarOngArquivadaPorPortoAjuda() {
        Usuario admin = usuario(22);
        Role roleAdmin = new Role();
        roleAdmin.setNome("ADMIN");
        admin.setRoles(new HashSet<>(Set.of(roleAdmin)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        admin, null, List.of(new SimpleGrantedAuthority("ADMIN_SESSION"))));
        osc.setOrigemUltimaDesativacao(OrigemDesativacao.PORTO_AJUDA);
        osc.setDataUltimaDesativacao(LocalDateTime.now().minusDays(3));
        osc.setDesativadoPorIdSnapshot(8);
        osc.setDataArquivamento(LocalDateTime.now().minusDays(1));
        osc.setMotivoArquivamento("Arquivamento solicitado pela organização");

        service.atualizarPelaPlataforma(OSC_ID,
                new RequestAtualizarStatusOscDTO(StatusOsc.ATIVA, "Revisão administrativa"), admin);

        assertEquals(StatusOsc.ATIVA, osc.getStatus());
        assertEquals(OrigemDesativacao.PORTO_AJUDA, osc.getOrigemUltimaDesativacao());
        assertNull(osc.getDataArquivamento());
        ArgumentCaptor<HistoricoStatusOsc> historico = ArgumentCaptor.forClass(HistoricoStatusOsc.class);
        verify(historicoRepository).save(historico.capture());
        assertEquals(TipoEventoHistoricoOsc.RESTAURACAO, historico.getValue().getTipoEvento());
        assertEquals(OrigemDesativacao.PORTO_AJUDA, historico.getValue().getOrigemDesativacaoAnterior());
        assertEquals(8, historico.getValue().getDesativadorAnteriorIdSnapshot());
        assertEquals("Arquivamento solicitado pela organização", historico.getValue().getMotivoArquivamentoAnterior());
    }

    @Test
    void usuarioSemRoleAdminNaoUsaTransicaoDaPlataforma() {
        AccessDeniedException erro = assertThrows(AccessDeniedException.class,
                () -> service.atualizarPelaPlataforma(OSC_ID,
                        new RequestAtualizarStatusOscDTO(StatusOsc.ATIVA, null), criador));

        verify(oscRepository, never()).save(osc);
        verifyNoInteractions(historicoRepository);
    }

    @Test
    void roleAdminSemSessaoAdministrativaNaoPodeModerarStatusDaOng() {
        Usuario admin = usuario(22);
        Role roleAdmin = new Role();
        roleAdmin.setNome("ADMIN");
        admin.setRoles(new HashSet<>(Set.of(roleAdmin)));

        AccessDeniedException erro = assertThrows(AccessDeniedException.class,
                () -> service.atualizarPelaPlataforma(OSC_ID,
                        new RequestAtualizarStatusOscDTO(StatusOsc.ATIVA, null), admin));

        verifyNoInteractions(oscRepository, historicoRepository);
    }

    private Usuario usuario(Integer id) {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }
}
