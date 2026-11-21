package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestDecisaoCandidaturaDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestFinalizarVoluntariadoDTO;
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
import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CandidaturaVoluntariadoService {

    private static final Set<StatusCandidatura> STATUS_QUE_BLOQUEIAM_REAPLICACAO = Set.of(
            StatusCandidatura.PENDENTE, StatusCandidatura.APROVADA, StatusCandidatura.RECUSADA,
            StatusCandidatura.CONCLUIDA, StatusCandidatura.CANCELADA_PELA_ONG);

    private final CandidaturasVoluntariadoRepository candidaturasRepository;
    private final VagaRepository vagaRepository;
    private final OscRepository oscRepository;
    private final OscService oscService;
    private final EntityManager entityManager;
    @Autowired(required = false) private VolunteerCertificateStorage certificateStorage;
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
        return new DisponibilidadeCandidaturaDTO(vaga != null && vagaBasicaDisponivel(vaga));
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
                .map(candidatura -> CandidaturaUsuarioDTO.from(candidatura, true,
                        vagaBasicaDisponivel(candidatura.getVaga())));
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
        if (candidatura.getStatus() != StatusCandidatura.PENDENTE
                && candidatura.getStatus() != StatusCandidatura.APROVADA) {
            throw conflito("Somente candidaturas pendentes ou participantes aprovados podem cancelar a participação.");
        }
        candidatura.setStatus(StatusCandidatura.DESISTIU);
        return CandidaturaUsuarioDTO.from(candidaturasRepository.save(candidatura),
                verificationService == null || verificationService.isPubliclyEligible(oscIdFromVaga(candidatura.getVaga())),
                vagaBasicaDisponivel(vaga));
    }

    @Transactional
    public Page<CandidaturaOngDTO> listarDaOsc(Integer oscId, StatusCandidatura status, Usuario solicitante, Pageable pageable) {
        return listarDaOsc(oscId, null, status, solicitante, pageable);
    }

    @Transactional
    public Page<CandidaturaOngDTO> listarDaOsc(Integer oscId, Integer vagaId, StatusCandidatura status, Usuario solicitante, Pageable pageable) {
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
        if (vagaId != null && vagaRepository.findById(vagaId)
                .filter(v -> v.getPost() != null && v.getPost().getOsc() != null && oscId.equals(v.getPost().getOsc().getId()))
                .isEmpty()) throw naoEncontrada();
        Pageable paginacao = paginacaoLimitada(pageable);
        return candidaturasRepository.listarPorOscVagaEStatus(osc.getId(), vagaId, status, paginacao)
                .map(candidatura -> CandidaturaOngDTO.from(candidatura, true));
    }

    @Transactional
    public Map<String, Object> finalizarVoluntariado(Integer oscId, Integer vagaId,
            RequestFinalizarVoluntariadoDTO request, Usuario actor) {
        exigirUsuario(actor);
        if (request == null || request.participantes() == null || request.dataInicioReal() == null || request.dataFimReal() == null
                || request.dataInicioReal().isAfter(request.dataFimReal()) || request.dataFimReal().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um período real válido para a atividade.");
        }
        if (oscId == null || vagaId == null) throw naoEncontrada();
        Osc osc = oscRepository.findForUpdateById(oscId).orElseThrow(CandidaturaVoluntariadoService::naoEncontrada);
        if (osc.isArquivada()) throw naoEncontrada();
        CargoOsc cargo = oscService.obterCargoUsuario(osc, actor);
        if (cargo != CargoOsc.CRIADOR && cargo != CargoOsc.ADMINISTRADOR) throw forbiddenManager();
        Vaga vaga = bloquearVaga(vagaId);
        if (vaga.getPost() == null || vaga.getPost().getOsc() == null || !oscId.equals(vaga.getPost().getOsc().getId())) throw naoEncontrada();
        if (vaga.getStatus() != StatusVaga.ABERTA || vaga.getDataConclusao() != null) {
            throw conflito("Esta oportunidade já foi encerrada ou finalizada.");
        }
        Set<Integer> selected = new HashSet<>();
        Map<Integer, BigDecimal> hoursByApplication = new HashMap<>();
        for (var participant : request.participantes()) {
            if (participant == null || participant.candidaturaId() == null || !Boolean.TRUE.equals(participant.concluiu())) continue;
            if (!selected.add(participant.candidaturaId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Há participantes duplicados na solicitação.");
            if (participant.horasRealizadas() == null || participant.horasRealizadas().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe as horas realizadas de cada participante concluinte.");
            }
            if (participant.horasRealizadas().compareTo(new BigDecimal("999999.99")) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "As horas realizadas excedem o limite por participante.");
            }
            hoursByApplication.put(participant.candidaturaId(), participant.horasRealizadas().setScale(2, java.math.RoundingMode.HALF_UP));
        }
        for (var participant : request.participantes()) {
            if (participant != null && participant.candidaturaId() != null && Boolean.FALSE.equals(participant.concluiu())
                    && participant.horasRealizadas() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horas só podem ser registradas para participantes que concluíram.");
            }
        }
        List<CandidaturasVoluntariado> applications = candidaturasRepository.findAllByVagaIdForUpdate(vagaId);
        Set<Integer> acceptedIds = new HashSet<>();
        for (var application : applications) {
            if (application.getStatus() == StatusCandidatura.APROVADA) acceptedIds.add(application.getId());
        }
        if (!acceptedIds.containsAll(selected)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione somente participantes aprovados nesta oportunidade.");
        int completed = 0, pendingCanceled = 0, acceptedCanceled = 0;
        BigDecimal totalHours = BigDecimal.ZERO;
        LocalDate completionDate = request.dataFimReal();
        List<Integer> completedApplicationIds = new ArrayList<>();
        for (var application : applications) {
            if (application.getStatus() == StatusCandidatura.PENDENTE) {
                application.setStatus(StatusCandidatura.CANCELADA_PELA_ONG); pendingCanceled++;
            } else if (application.getStatus() == StatusCandidatura.APROVADA) {
                if (selected.contains(application.getId())) {
                    application.setStatus(StatusCandidatura.CONCLUIDA);
                    application.setDataConclusao(completionDate);
                    BigDecimal participantHours = hoursByApplication.get(application.getId());
                    application.setHorasRealizadas(participantHours);
                    totalHours = totalHours.add(participantHours);
                    completed++; completedApplicationIds.add(application.getId());
                } else {
                    application.setStatus(StatusCandidatura.CANCELADA_PELA_ONG); acceptedCanceled++;
                }
            }
        }
        if (!applications.stream().map(CandidaturasVoluntariado::getId).collect(java.util.stream.Collectors.toSet()).containsAll(selected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Há candidaturas que não pertencem a esta oportunidade.");
        }
        if (totalHours.compareTo(new BigDecimal("999999.99")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A soma das horas excede o limite permitido para a oportunidade.");
        }
        candidaturasRepository.saveAll(applications);
        vaga.setStatus(StatusVaga.ENCERRADA);
        vaga.setDataInicioReal(request.dataInicioReal()); vaga.setDataFimReal(request.dataFimReal());
        vaga.setHorasTotaisRealizadas(totalHours.setScale(2, java.math.RoundingMode.HALF_UP));
        vaga.setDataConclusao(Instant.now());
        vagaRepository.save(vaga);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("vagaId", vagaId); result.put("dataConclusao", completionDate);
        result.put("completedCount", completed); result.put("canceledPendingCount", pendingCanceled);
        result.put("canceledAcceptedCount", acceptedCanceled); result.put("totalHoursRealizadas", totalHours);
        result.put("completedApplicationIds", completedApplicationIds);
        return result;
    }

    @Transactional
    public void uploadCertificate(Integer oscId, Integer candidaturaId, MultipartFile file, Usuario actor) {
        exigirManagerOrApplicant(oscId, candidaturaId, actor, true);
        CandidaturasVoluntariado application = candidaturasRepository.findByIdAndVaga_Post_Osc_Id(candidaturaId, oscId)
                .orElseThrow(CandidaturaVoluntariadoService::naoEncontrada);
        if (application.getStatus() != StatusCandidatura.CONCLUIDA) throw conflito("Certificados podem ser enviados somente para participantes concluídos.");
        if (application.getCertificadoObjectKey() != null) throw conflito("Esta conclusão já possui um certificado anexado.");
        if (certificateStorage == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O armazenamento privado de certificados não está disponível.");
        FileInfo info = validateCertificate(file);
        String key = certificateStorage.store(oscId, candidaturaId, info.extension(), info.contentType(), info.bytes());
        application.setCertificadoObjectKey(key);
        candidaturasRepository.save(application);
    }

    @Transactional(readOnly = true)
    public String certificateUrl(Integer candidaturaId, Usuario actor) {
        exigirUsuario(actor);
        CandidaturasVoluntariado application = candidaturasRepository.findByIdAndUsuario_Id(candidaturaId, actor.getId())
                .orElseGet(() -> candidaturasRepository.findById(candidaturaId).orElseThrow(CandidaturaVoluntariadoService::naoEncontrada));
        Integer oscId = oscIdFromVaga(application.getVaga());
        if (!actor.getId().equals(application.getUsuario().getId())) requireManager(oscId, actor);
        if (application.getStatus() != StatusCandidatura.CONCLUIDA || application.getCertificadoObjectKey() == null) throw naoEncontrada();
        if (certificateStorage == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O armazenamento privado de certificados não está disponível.");
        return certificateStorage.createShortLivedAccess(application.getCertificadoObjectKey()).url();
    }

    private void exigirManagerOrApplicant(Integer oscId, Integer candidaturaId, Usuario actor, boolean managerOnly) {
        exigirUsuario(actor);
        if (managerOnly) requireManager(oscId, actor);
    }

    private void requireManager(Integer oscId, Usuario actor) {
        Osc osc = oscRepository.findById(oscId).orElseThrow(CandidaturaVoluntariadoService::naoEncontrada);
        CargoOsc cargo = oscService.obterCargoUsuario(osc, actor);
        if (cargo != CargoOsc.CRIADOR && cargo != CargoOsc.ADMINISTRADOR) throw forbiddenManager();
    }

    private FileInfo validateCertificate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um certificado.");
        if (file.getSize() > 10L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "O certificado precisa ter até 10 MB.");
        try {
            byte[] bytes = file.getBytes(); String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
            if (type.equals("application/pdf") && starts(bytes, new byte[]{0x25,0x50,0x44,0x46})) return new FileInfo("pdf", type, bytes);
            if (type.equals("image/jpeg") && bytes.length > 3 && (bytes[0]&255)==0xff && (bytes[1]&255)==0xd8 && (bytes[2]&255)==0xff) return new FileInfo("jpg",type,bytes);
            if (type.equals("image/png") && starts(bytes,new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a})) return new FileInfo("png",type,bytes);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie um arquivo PDF, JPEG ou PNG válido.");
        } catch (java.io.IOException failure) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Não foi possível ler o certificado."); }
    }
    private static boolean starts(byte[] data, byte[] expected) { if(data.length<expected.length)return false; for(int i=0;i<expected.length;i++)if(data[i]!=expected[i])return false; return true; }
    private record FileInfo(String extension,String contentType,byte[] bytes) {}

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
                || vaga.getQuantidadeVagas() < 1 || vaga.getDataFim() == null || vaga.getDataFim().isBefore(hoje)
                || vaga.getDataConclusao() != null
                || candidaturasRepository.countByVaga_IdAndStatus(vaga.getId(), StatusCandidatura.APROVADA) >= vaga.getQuantidadeVagas());
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
    private static ResponseStatusException forbiddenManager() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente CRIADOR ou ADMINISTRADOR desta ONG pode realizar esta ação.");
    }
}
