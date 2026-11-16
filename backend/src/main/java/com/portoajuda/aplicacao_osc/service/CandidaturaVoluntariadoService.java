package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestDecisaoCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.response.CandidaturaOngDTO;
import com.portoajuda.aplicacao_osc.dto.response.CandidaturaUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.DisponibilidadeCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.response.SituacaoMinhaCandidaturaDTO;
import com.portoajuda.aplicacao_osc.entity.CandidaturasVoluntariado;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.repository.CandidaturasVoluntariadoRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.VagaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CandidaturaVoluntariadoService {

    private static final Set<StatusCandidatura> STATUS_QUE_BLOQUEIAM_REAPLICACAO = Set.of(
            StatusCandidatura.PENDENTE, StatusCandidatura.APROVADA, StatusCandidatura.RECUSADA);

    private final CandidaturasVoluntariadoRepository candidaturasRepository;
    private final VagaRepository vagaRepository;
    private final OscRepository oscRepository;
    private final OscService oscService;
    private final EntityManager entityManager;
    @Autowired(required = false)
    private ContentReportService contentReports;
    @Autowired(required = false)
    private ModerationPointsService moderationPoints;
    @Autowired(required = false)
    private VerificacaoRepresentanteService verificationService;

    @Transactional(readOnly = true)
    public DisponibilidadeCandidaturaDTO disponibilidadePublica(Integer vagaId) {
        if (vagaId == null || vagaId < 1) return new DisponibilidadeCandidaturaDTO(false);
        Vaga vaga = vagaRepository.findById(vagaId).orElse(null);
        if (vaga == null || !vagaBasicaDisponivel(vaga)) return new DisponibilidadeCandidaturaDTO(false);
        long aprovadas = candidaturasRepository.countByVaga_IdAndStatus(vagaId, StatusCandidatura.APROVADA);
        return new DisponibilidadeCandidaturaDTO(aprovadas < vaga.getQuantidadeVagas());
    }

    @Transactional(readOnly = true)
    public SituacaoMinhaCandidaturaDTO situacaoMinhaCandidatura(Integer vagaId, Usuario usuario) {
        exigirUsuario(usuario);
        if (vagaId == null || vagaId < 1) return new SituacaoMinhaCandidaturaDTO(false, null);
        return candidaturasRepository.findFirstByVaga_IdAndUsuario_IdOrderByDataCandidaturaDesc(vagaId, usuario.getId())
                .filter(candidatura -> candidatura.getStatus() != StatusCandidatura.DESISTIU)
                .map(candidatura -> new SituacaoMinhaCandidaturaDTO(true, candidatura.getStatus()))
                .orElseGet(() -> new SituacaoMinhaCandidaturaDTO(false, null));
    }

    @Transactional
    public CandidaturaUsuarioDTO candidatar(Integer vagaId, RequestCandidaturaDTO request, Usuario usuario) {
        exigirUsuario(usuario);
        if (vagaId == null || vagaId < 1) throw naoEncontrada();
        Vaga referencia = vagaRepository.findById(vagaId)
                .orElseThrow(CandidaturaVoluntariadoService::naoEncontrada);
        if(moderationPoints!=null)moderationPoints.requireBusinessMutationAllowed(usuario.getId(),oscIdFromVaga(referencia));
        Osc oscBloqueada = bloquearOscDaVaga(referencia);
        Vaga vaga = bloquearVaga(vagaId);
        validarVagaPublicaDisponivel(vaga, oscBloqueada);
        if (candidaturasRepository.existsByVaga_IdAndUsuario_IdAndStatusIn(
                vagaId, usuario.getId(), STATUS_QUE_BLOQUEIAM_REAPLICACAO)) {
            throw conflito("Você já possui uma candidatura ativa ou encerrada para esta oportunidade.");
        }
        long aprovadas = candidaturasRepository.countByVaga_IdAndStatus(vagaId, StatusCandidatura.APROVADA);
        if (aprovadas >= vaga.getQuantidadeVagas()) {
            throw conflito("Todas as vagas disponíveis já foram preenchidas.");
        }

        CandidaturasVoluntariado candidatura = new CandidaturasVoluntariado();
        candidatura.setVaga(vaga);
        candidatura.setUsuario(usuario);
        candidatura.setMensagem(request == null || request.mensagem() == null ? null : request.mensagem().trim());
        candidatura.setStatus(StatusCandidatura.PENDENTE);
        candidatura.setDataCandidatura(LocalDate.now());
        return CandidaturaUsuarioDTO.from(candidaturasRepository.save(candidatura),
                verificationService == null || verificationService.isPubliclyEligible(oscBloqueada.getId()));
    }

    @Transactional(readOnly = true)
    public Page<CandidaturaUsuarioDTO> listarMinhas(Usuario usuario, Pageable pageable) {
        exigirUsuario(usuario);
        Pageable paginacao = paginacaoLimitada(pageable);
        return candidaturasRepository.findAllByUsuario_IdOrderByDataCandidaturaDesc(usuario.getId(), paginacao)
                .map(candidatura -> CandidaturaUsuarioDTO.from(candidatura,
                        verificationService == null || verificationService.isPubliclyEligible(oscIdFromVaga(candidatura.getVaga()))));
    }

    @Transactional
    public CandidaturaUsuarioDTO cancelarMinha(Integer candidaturaId, Usuario usuario) {
        exigirUsuario(usuario);
        CandidaturasVoluntariado inicial = candidaturasRepository.findByIdAndUsuario_Id(candidaturaId, usuario.getId())
                .orElseThrow(CandidaturaVoluntariadoService::naoEncontrada);
        if(moderationPoints!=null)moderationPoints.requireBusinessMutationAllowed(usuario.getId(),oscIdFromVaga(inicial.getVaga()));
        Vaga vaga = bloquearVaga(inicial.getVaga().getId());
        entityManager.refresh(inicial, LockModeType.PESSIMISTIC_WRITE);
        CandidaturasVoluntariado candidatura = inicial;
        if (candidatura.getVaga().getId().equals(vaga.getId()) == false) {
            throw naoEncontrada();
        }
        if (candidatura.getStatus() != StatusCandidatura.PENDENTE) {
            throw conflito("Somente candidaturas pendentes podem ser canceladas.");
        }
        candidatura.setStatus(StatusCandidatura.DESISTIU);
        return CandidaturaUsuarioDTO.from(candidaturasRepository.save(candidatura),
                verificationService == null || verificationService.isPubliclyEligible(oscIdFromVaga(candidatura.getVaga())));
    }

    @Transactional
    public Page<CandidaturaOngDTO> listarDaOsc(Integer oscId, StatusCandidatura status, Usuario solicitante, Pageable pageable) {
        exigirUsuario(solicitante);
        if (oscId == null || oscId < 1) throw naoEncontrada();
        if(moderationPoints!=null)moderationPoints.requireBusinessMutationAllowed(solicitante.getId(),oscId);
        Osc osc = oscRepository.findForUpdateById(oscId)
                .orElseThrow(CandidaturaVoluntariadoService::naoEncontrada);
        if (osc.isArquivada()) throw naoEncontrada();
        CargoOsc cargo = oscService.obterCargoUsuario(osc, solicitante);
        if (cargo != CargoOsc.CRIADOR && cargo != CargoOsc.ADMINISTRADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente CRIADOR ou ADMINISTRADOR pode consultar candidaturas desta ONG.");
        }
        Pageable paginacao = paginacaoLimitada(pageable);
        return candidaturasRepository.listarPorOscEStatus(osc.getId(), status, paginacao)
                .map(candidatura -> CandidaturaOngDTO.from(candidatura, true));
    }

    @Transactional
    public CandidaturaOngDTO decidir(Integer oscId, Integer candidaturaId,
            RequestDecisaoCandidaturaDTO request, Usuario solicitante) {
        if (request == null || (request.status() != StatusCandidatura.APROVADA
                && request.status() != StatusCandidatura.RECUSADA)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A decisão deve ser APROVADA ou RECUSADA.");
        }
        exigirUsuario(solicitante);
        if (oscId == null || oscId < 1) throw naoEncontrada();
        Osc osc = oscRepository.findForUpdateById(oscId)
                .orElseThrow(CandidaturaVoluntariadoService::naoEncontrada);
        if (osc.isArquivada()) throw naoEncontrada();
        CargoOsc cargo = oscService.obterCargoUsuario(osc, solicitante);
        if (cargo != CargoOsc.CRIADOR && cargo != CargoOsc.ADMINISTRADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente CRIADOR ou ADMINISTRADOR pode decidir candidaturas.");
        }

        CandidaturasVoluntariado inicial = candidaturasRepository.findByIdAndVaga_Post_Osc_Id(candidaturaId, oscId)
                .orElseThrow(CandidaturaVoluntariadoService::naoEncontrada);
        Vaga vaga = bloquearVaga(inicial.getVaga().getId());
        entityManager.refresh(inicial, LockModeType.PESSIMISTIC_WRITE);
        CandidaturasVoluntariado candidatura = inicial;
        if (!candidatura.getVaga().getId().equals(vaga.getId())) throw naoEncontrada();
        if (candidatura.getStatus() != StatusCandidatura.PENDENTE) {
            throw conflito("Esta candidatura já recebeu uma decisão e não pode ser alterada.");
        }

        if (request.status() == StatusCandidatura.APROVADA) {
            validarVagaPublicaDisponivel(vaga, osc);
            long aprovadas = candidaturasRepository.countByVaga_IdAndStatus(
                    vaga.getId(), StatusCandidatura.APROVADA);
            int capacidade = vaga.getQuantidadeVagas() == null ? 0 : vaga.getQuantidadeVagas();
            if (aprovadas >= capacidade) {
                throw conflito("Todas as vagas disponíveis já foram aprovadas.");
            }
        }
        candidatura.setStatus(request.status());
        return CandidaturaOngDTO.from(candidaturasRepository.save(candidatura), true);
    }

    private Osc bloquearOscDaVaga(Vaga vaga) {
        var post = vaga == null ? null : vaga.getPost();
        var osc = post == null ? null : post.getOsc();
        if (osc == null || osc.getId() == null) throw naoEncontrada();
        return oscRepository.findForUpdateById(osc.getId())
                .orElseThrow(CandidaturaVoluntariadoService::naoEncontrada);
    }

    private Integer oscIdFromVaga(Vaga vaga) {
        return vaga == null || vaga.getPost() == null || vaga.getPost().getOsc() == null
                ? null : vaga.getPost().getOsc().getId();
    }

    private Vaga bloquearVaga(Integer vagaId) {
        if (vagaId == null || vagaId < 1) throw naoEncontrada();
        return vagaRepository.findForUpdateById(vagaId).orElseThrow(CandidaturaVoluntariadoService::naoEncontrada);
    }

    private void validarVagaPublicaDisponivel(Vaga vaga, Osc oscAtual) {
        if (!vagaBasicaDisponivel(vaga)) {
            throw conflito("Esta oportunidade não está aceitando candidaturas.");
        }
        if (oscAtual.getStatus() != StatusOsc.ATIVA || oscAtual.isArquivada()
                || vaga.getPost() == null || vaga.getPost().getOsc() == null
                || !oscAtual.getId().equals(vaga.getPost().getOsc().getId())
                || protecaoAtiva("OSC", oscAtual.getId())
                || protecaoAtiva("POST", vaga.getPost().getId())) {
            throw conflito("Esta oportunidade não está aceitando candidaturas.");
        }
    }

    private boolean vagaBasicaDisponivel(Vaga vaga) {
        var post = vaga.getPost();
        var osc = post == null ? null : post.getOsc();
        LocalDate hoje = LocalDate.now();
        return !(post == null || post.getTipo() != TipoPost.VOLUNTARIADO || !post.isAtivo()
                || osc == null || osc.getStatus() != StatusOsc.ATIVA || osc.isArquivada()
                || (verificationService != null && !verificationService.isPubliclyEligible(osc.getId()))
                || protecaoAtiva("OSC", osc.getId()) || protecaoAtiva("POST", post.getId())
                || vaga.getStatus() != StatusVaga.ABERTA || vaga.getQuantidadeVagas() == null
                || vaga.getQuantidadeVagas() < 1 || vaga.getDataFim() == null || vaga.getDataFim().isBefore(hoje));
    }

    private boolean protecaoAtiva(String tipo, Integer id) {
        return contentReports != null && contentReports.isBlocked(tipo, id);
    }

    private Pageable paginacaoLimitada(Pageable pageable) {
        int pagina = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        int tamanho = pageable == null ? 20 : Math.min(Math.max(1, pageable.getPageSize()), 50);
        return PageRequest.of(pagina, tamanho);
    }

    private void exigirUsuario(Usuario usuario) {
        if (usuario == null || usuario.getId() == null || !usuario.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }
    }

    private static ResponseStatusException naoEncontrada() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidatura ou oportunidade não encontrada.");
    }

    private static ResponseStatusException conflito(String mensagem) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
    }
}
