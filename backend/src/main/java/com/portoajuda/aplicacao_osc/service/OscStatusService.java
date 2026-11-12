package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.admin.AdminActor;
import com.portoajuda.aplicacao_osc.admin.AdminAuthorizationService;
import com.portoajuda.aplicacao_osc.admin.AdminOperationAuditService;
import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.dto.request.RequestAtualizarStatusOscDTO;
import com.portoajuda.aplicacao_osc.entity.HistoricoStatusOsc;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.OrigemDesativacao;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.TipoEventoHistoricoOsc;
import com.portoajuda.aplicacao_osc.repository.HistoricoStatusOscRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class OscStatusService {

    private final OscRepository oscRepository;
    private final HistoricoStatusOscRepository historicoRepository;
    private final OscService oscService;
    private final AdminAuthorizationService adminAuthorization;
    private final AdminOperationAuditService operationAudit;

    public OscStatusService(
            OscRepository oscRepository,
            HistoricoStatusOscRepository historicoRepository,
            OscService oscService,
            AdminAuthorizationService adminAuthorization,
            AdminOperationAuditService operationAudit) {
        this.oscRepository = oscRepository;
        this.historicoRepository = historicoRepository;
        this.oscService = oscService;
        this.adminAuthorization = adminAuthorization;
        this.operationAudit = operationAudit;
    }

    @Transactional
    public void atualizarPorOrganizacao(
            Integer oscId,
            RequestAtualizarStatusOscDTO request,
            Usuario ator) {
        Osc osc = buscarParaAtualizacao(oscId);
        CargoOsc cargo = oscService.obterCargoUsuario(osc, ator);
        if (cargo != CargoOsc.CRIADOR && cargo != CargoOsc.ADMINISTRADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente o criador ou administrador da organização pode alterar o status.");
        }

        aplicarTransicao(osc, request, ator, OrigemDesativacao.ONG, cargo.name(), false);
    }

    @Transactional
    public void atualizarPelaPlataforma(
            Integer oscId,
            RequestAtualizarStatusOscDTO request,
            Usuario ator) {
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o status solicitado.");
        }
        AdminActor sessionActor = adminAuthorization.requireValidPrivilegedSession();
        Osc osc = buscarParaAtualizacao(oscId);
        AdminPermission permission = request.status() == StatusOsc.INATIVA
                ? AdminPermission.ORG_DEACTIVATE
                : osc.isArquivada()
                        ? AdminPermission.ORG_RESTORE_ARCHIVED
                        : AdminPermission.ORG_REACTIVATE;
        AdminActor actor = adminAuthorization.requirePermission(permission);
        if (ator == null || ator.getId() == null || !ator.getId().equals(actor.userId())
                || !sessionActor.userId().equals(actor.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O ator da operação deve ser a sessão administrativa autenticada.");
        }
        String reason = adminAuthorization.requireReason(request.motivo());
        RequestAtualizarStatusOscDTO requestParaHistorico =
                new RequestAtualizarStatusOscDTO(request.status(), reason);
        StatusOsc previousStatus = osc.getStatus();
        boolean wasArchived = osc.isArquivada();
        String authority = actor.profile() == null ? "ROLE_ADMIN" : actor.profile().name();
        aplicarTransicao(osc, requestParaHistorico, ator, OrigemDesativacao.PORTO_AJUDA,
                authority + ":" + permission.name(), true);
        operationAudit.record(actor, permission, "ORGANIZATION_STATUS_CHANGED", "OSC",
                oscId.toString(), reason,
                "status=" + previousStatus + ";archived=" + wasArchived,
                "status=" + request.status() + ";archived=" + osc.isArquivada());
    }

    private Osc buscarParaAtualizacao(Integer oscId) {
        return oscRepository.findForUpdateById(oscId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Organização não encontrada."));
    }

    private void aplicarTransicao(
            Osc osc,
            RequestAtualizarStatusOscDTO request,
            Usuario ator,
            OrigemDesativacao origemAcao,
            String autoridadeAtor,
            boolean plataforma) {
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o status solicitado.");
        }
        if (ator == null || ator.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }
        boolean restaurando = osc.isArquivada();
        if (restaurando && request.status() != StatusOsc.ATIVA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Uma organização arquivada só pode ser restaurada por uma reativação autorizada.");
        }
        if (!restaurando && osc.getStatus() == request.status()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A organização já está no status informado.");
        }

        if (request.status() == StatusOsc.ATIVA && !plataforma) {
            OrigemDesativacao origemAnterior = osc.getOrigemUltimaDesativacao();
            if (origemAnterior == OrigemDesativacao.PORTO_AJUDA
                    || origemAnterior == OrigemDesativacao.INDETERMINADA) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Somente a administração do Porto Ajuda pode reativar esta organização.");
            }
        }

        StatusOsc statusAnterior = osc.getStatus();
        OrigemDesativacao origemDesativacaoAnterior = osc.getOrigemUltimaDesativacao();
        LocalDateTime dataDesativacaoAnterior = osc.getDataUltimaDesativacao();
        Usuario desativadorAnterior = osc.getDesativadoPor();
        Integer desativadorAnteriorIdSnapshot = osc.getDesativadoPorIdSnapshot();
        String motivoDesativacaoAnterior = osc.getMotivoUltimaDesativacao();
        LocalDateTime dataArquivamentoAnterior = osc.getDataArquivamento();
        Usuario arquivadorAnterior = osc.getArquivadaPor();
        Integer arquivadorAnteriorIdSnapshot = osc.getArquivadaPorIdSnapshot();
        String motivoArquivamentoAnterior = osc.getMotivoArquivamento();
        osc.setStatus(request.status());
        if (request.status() == StatusOsc.INATIVA) {
            osc.setOrigemUltimaDesativacao(origemAcao);
            osc.setDataUltimaDesativacao(LocalDateTime.now());
            osc.setDesativadoPor(ator);
            osc.setDesativadoPorIdSnapshot(ator.getId());
            osc.setMotivoUltimaDesativacao(normalizarMotivo(request.motivo()));
        }
        if (restaurando) {
            osc.setDataArquivamento(null);
            osc.setArquivadaPor(null);
            osc.setArquivadaPorIdSnapshot(null);
            osc.setMotivoArquivamento(null);
        }

        oscRepository.save(osc);
        historicoRepository.save(HistoricoStatusOsc.registrar(
                osc,
                statusAnterior,
                request.status(),
                origemAcao,
                restaurando ? TipoEventoHistoricoOsc.RESTAURACAO : TipoEventoHistoricoOsc.TRANSICAO_STATUS,
                origemDesativacaoAnterior,
                dataDesativacaoAnterior,
                desativadorAnterior,
                desativadorAnteriorIdSnapshot,
                motivoDesativacaoAnterior,
                dataArquivamentoAnterior,
                arquivadorAnterior,
                arquivadorAnteriorIdSnapshot,
                motivoArquivamentoAnterior,
                ator,
                autoridadeAtor,
                normalizarMotivo(request.motivo())));
    }

    private String normalizarMotivo(String motivo) {
        return motivo == null || motivo.isBlank() ? null : motivo.trim();
    }
}
