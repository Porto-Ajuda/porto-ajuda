package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestNewMembroDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestOscDTO;
import com.portoajuda.aplicacao_osc.dto.request.UpdateOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.dto.response.ConsultaCnpjDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscListagemDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscMembroDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscPerfilDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscEdicaoDTO;
import com.portoajuda.aplicacao_osc.entity.*;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.Email;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OscService {

    private static final Logger log = LoggerFactory.getLogger(OscService.class);
    private static final Set<String> AREAS_GEOGRAFICAS = Set.of(
            "bairro", "cidade", "municipal", "regional", "estadual", "nacional", "internacional");

    private final OscRepository oscRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final OscMembrosRepository membrosRepository;
    private final GeocodingService geocodingService;
    private final ValidacaoCnpjOscService validacaoCnpjOscService;
    private final OscPersistenciaService persistenciaService;
    private final SupabaseStorageService supabaseStorageService;
    private final LimpezaService limpezaService;

    // =====================================================
    // PERMISSÕES
    // =====================================================

    private CargoOsc obterCargoUsuario(Osc osc, Usuario usuario) {

        if (usuario == null || usuario.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Autenticação necessária.");
        }

        Integer criadorId = osc.getUsuario() == null ? null : osc.getUsuario().getId();
        if (criadorId == null) {
            log.warn("A OSC {} não possui um criador original válido.", osc.getId());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "O vínculo do criador desta organização está inconsistente.");
        }
        boolean donoOriginal = criadorId.equals(usuario.getId());
        OscMembros vinculo = membrosRepository
                .findByOsc_IdAndUsuario_Id(osc.getId(), usuario.getId())
                .orElse(null);
        if (vinculo == null) {
            if (donoOriginal) {
                log.warn("Criador sem vínculo de membro na OSC {} para usuário {}.",
                        osc.getId(), usuario.getId());
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "O vínculo do criador desta organização está inconsistente.");
            }
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Você não pertence a esta organização.");
        }
        if (donoOriginal && vinculo.getCargoOsc() != CargoOsc.CRIADOR) {
            log.warn("Vínculo do criador inconsistente para a OSC {} e usuário {}.",
                    osc.getId(), usuario.getId());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "O vínculo do criador desta organização está inconsistente.");
        }
        if (!donoOriginal && vinculo.getCargoOsc() == CargoOsc.CRIADOR) {
            log.warn("Cargo CRIADOR inconsistente para a OSC {} e usuário {}.",
                    osc.getId(), usuario.getId());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "O vínculo do criador desta organização está inconsistente.");
        }

        return vinculo.getCargoOsc();
    }

    private void exigirCargo(
            Osc osc,
            Usuario usuario,
            CargoOsc... cargosPermitidos) {

        CargoOsc cargo = obterCargoUsuario(osc, usuario);

        for (CargoOsc permitido : cargosPermitidos) {
            if (cargo == permitido) {
                return;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Você não possui permissão para realizar esta ação.");
    }

    // =====================================================
    // CADASTRO
    // =====================================================

    public ResponseOscDTO create(
            RequestOscDTO oscDTO,
            Usuario usuarioAuth,
            MultipartFile logo) {

        if (usuarioAuth == null || usuarioAuth.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Entre na sua conta antes de cadastrar uma OSC.");
        }

        if (oscRepository.existsByCnpj(new Cnpj(oscDTO.cnpj()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma organização cadastrada com este CNPJ.");
        }

        validarCnpjNoCadastro(oscDTO.cnpj());

        Coordenadas coordenadas = geocodingService.buscarCoordenadas(
                oscDTO.pais(),
                oscDTO.estado(),
                oscDTO.cep(),
                oscDTO.cidade(),
                oscDTO.rua(),
                oscDTO.numero());

        SupabaseStorageService.ArquivoEnviado arquivoLogo = null;

        try {
            if (logo != null && !logo.isEmpty()) {
                try {
                    arquivoLogo = supabaseStorageService.enviarLogoOsc(
                            usuarioAuth.getId(),
                            obterBytes(logo),
                            logo.getContentType());

                } catch (IllegalArgumentException erro) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            erro.getMessage());

                } catch (RestClientException erro) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Não foi possível salvar a logo agora. Tente novamente.",
                            erro);
                }
            }

            return persistenciaService.salvar(
                    oscDTO,
                    usuarioAuth.getId(),
                    coordenadas,
                    arquivoLogo == null ? null : arquivoLogo.url());

        } catch (DataIntegrityViolationException erro) {
            removerLogoSeNecessario(arquivoLogo);

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "O CNPJ já está cadastrado ou os dados conflitam com uma restrição existente.",
                    erro);

        } catch (RuntimeException erro) {
            removerLogoSeNecessario(arquivoLogo);
            throw erro;
        }
    }

    private void validarCnpjNoCadastro(String cnpj) {

        ConsultaCnpjDTO consulta;

        try {
            consulta = validacaoCnpjOscService.validar(cnpj);

        } catch (IllegalStateException erro) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Não foi possível validar o CNPJ agora. Tente novamente mais tarde.",
                    erro);
        }

        String solicitado = cnpj.replaceAll("\\D", "");

        String consultado = consulta == null || consulta.cnpj() == null
                ? ""
                : consulta.cnpj().replaceAll("\\D", "");

        if (!solicitado.equals(consultado)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "A consulta externa não confirmou o CNPJ informado.");
        }

        if (!consulta.elegivel()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    consulta.motivo() == null
                            ? "O CNPJ não atende aos critérios de cadastro."
                            : consulta.motivo());
        }
    }

    private byte[] obterBytes(MultipartFile arquivo) {

        try {
            return arquivo.getBytes();

        } catch (IOException erro) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Não foi possível ler o arquivo de logo enviado.",
                    erro);
        }
    }

    private void removerLogoSeNecessario(
            SupabaseStorageService.ArquivoEnviado logo) {

        if (logo == null) {
            return;
        }

        try {
            supabaseStorageService.removerLogoOsc(logo.caminho());

        } catch (RuntimeException erroLimpeza) {
            org.slf4j.LoggerFactory.getLogger(OscService.class)
                    .warn("Falha ao limpar logo temporário após erro de persistência.");
        }
    }

    // =====================================================
    // ATUALIZAÇÃO DA OSC
    // CRIADOR, ADMINISTRADOR E EDITOR
    // =====================================================

    @Transactional
    public OscEdicaoDTO update(UpdateOscDTO updateOscDTO, Usuario usuario) {

        Osc osc = oscRepository.findForUpdateByCnpj(
                new Cnpj(updateOscDTO.cnpj()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "CNPJ não encontrado."));

        exigirCargo(
                osc,
                usuario,
                CargoOsc.CRIADOR,
                CargoOsc.ADMINISTRADOR,
                CargoOsc.EDITOR);

        osc.setDescricao(updateOscDTO.descricao());
        osc.setEmail(new Email(updateOscDTO.email()));
        osc.setTelefone(updateOscDTO.telefone());
        osc.setSite(updateOscDTO.site());

        if (updateOscDTO.nome() != null || updateOscDTO.descricaoCurta() != null) {
            atualizarCamposEditaveis(updateOscDTO, osc);
        }

        if (updateOscDTO.causaPrincipal() != null
                || updateOscDTO.causasSecundarias() != null) {

            CausaSocial causaPrincipal = updateOscDTO.causaPrincipal() == null
                    ? osc.getCausaPrincipal()
                    : updateOscDTO.causaPrincipal();

            Set<CausaSocial> causasSecundarias = updateOscDTO.causasSecundarias() == null
                    ? new HashSet<>(osc.getCausasSecundarias())
                    : new HashSet<>(updateOscDTO.causasSecundarias());

            if (causasSecundarias.contains(null)) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "As causas secundárias não podem conter valores nulos.");
            }

            if (causaPrincipal != null
                    && causasSecundarias.contains(causaPrincipal)) {

                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "A causa principal não pode aparecer entre as causas secundárias.");
            }
            if (causaPrincipal == null && causasSecundarias.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Selecione pelo menos uma causa para a organização.");
            }

            osc.setCausasSociais(causaPrincipal, causasSecundarias);
        }

        if (updateOscDTO.ods() != null) {
            osc.setOds(validarOds(updateOscDTO.ods()));
        }

        oscRepository.save(osc);
        return OscEdicaoDTO.de(osc);
    }

    private void atualizarCamposEditaveis(UpdateOscDTO dados, Osc osc) {
        if (dados.anoFundacao() != null && dados.anoFundacao() > java.time.LocalDate.now().getYear()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O ano de fundação não pode estar no futuro.");
        }
        if ((dados.horarioAbertura() == null) != (dados.horarioFechamento() == null)
                || (dados.horarioAbertura() != null && !dados.horarioFechamento().isAfter(dados.horarioAbertura()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe horários de abertura e fechamento válidos.");
        }
        if (dados.causaPrincipal() != null && dados.causasSecundarias() != null
                && dados.causasSecundarias().contains(dados.causaPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A causa principal não pode aparecer entre as causas secundárias.");
        }
        if (dados.causasSecundarias() != null && dados.causasSecundarias().stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "As causas secundárias não podem conter valores nulos.");
        }
        String areaNormalizada = dados.areaGeografica() == null ? "" : dados.areaGeografica().trim().toLowerCase(java.util.Locale.ROOT);
        if (!AREAS_GEOGRAFICAS.contains(areaNormalizada)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A área geográfica selecionada é inválida.");
        }

        boolean enderecoAlterado = !java.util.Objects.equals(osc.getPais(), dados.pais())
                || !java.util.Objects.equals(osc.getEstado(), dados.estado())
                || !java.util.Objects.equals(osc.getCep(), dados.cep())
                || !java.util.Objects.equals(osc.getCidade(), dados.cidade())
                || !java.util.Objects.equals(osc.getRua(), dados.rua())
                || !java.util.Objects.equals(osc.getNumero(), dados.numero());
        if (enderecoAlterado) {
            Coordenadas coordenadas = geocodingService.buscarCoordenadas(
                    dados.pais(), dados.estado(), dados.cep(), dados.cidade(), dados.rua(), dados.numero());
            osc.setLatitude(coordenadas.latitude());
            osc.setLongitude(coordenadas.longitude());
        }

        if (dados.nome() != null) osc.setNome(dados.nome().trim());
        if (dados.descricaoCurta() != null) osc.setDescricaoCurta(dados.descricaoCurta().trim());
        if (dados.anoFundacao() != null) osc.setAnoFundacao(dados.anoFundacao());
        osc.setWhatsapp(limparOpcional(dados.whatsapp()));
        osc.setLinkedin(limparOpcional(dados.linkedin()));
        osc.setInstagram(limparOpcional(dados.instagram()));
        osc.setYoutube(limparOpcional(dados.youtube()));
        osc.setAreaGeografica(areaNormalizada);
        osc.setPais(dados.pais().trim());
        osc.setEstado(limparOpcional(dados.estado()));
        osc.setCidade(dados.cidade().trim());
        osc.setCep(dados.cep().trim());
        osc.setBairro(dados.bairro().trim());
        osc.setRua(dados.rua().trim());
        osc.setNumero(limparOpcional(dados.numero()));
        osc.setComplemento(limparOpcional(dados.complemento()));
        osc.setDiasHorariosFuncionamento(limparOpcional(dados.diasHorariosFuncionamento()));
        osc.setHorarioAbertura(dados.horarioAbertura());
        osc.setHorarioFechamento(dados.horarioFechamento());
        osc.setAtendimentoPresencial(dados.atendimentoPresencial());
        osc.setAtendimentoOnline(dados.atendimentoOnline());
        osc.setNecessitaAgendamento(dados.necessitaAgendamento());
        osc.setAceitaVoluntarios(dados.aceitaVoluntarios());
        osc.setFrequenciaVoluntariado(limparOpcional(dados.frequenciaVoluntariado()));
        osc.setIdadeMinimaVoluntario(dados.idadeMinimaVoluntario());
        osc.setProcessoSeletivoVoluntario(dados.processoSeletivoVoluntario());
        osc.setEmiteCertificadoVoluntario(dados.emiteCertificadoVoluntario());
        osc.setAtividadesVoluntariado(limparOpcional(dados.atividadesVoluntariado()));
    }

    private String limparOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Set<Integer> validarOds(Set<Integer> valores) {

        Set<Integer> ods = valores == null
                ? new HashSet<>()
                : new HashSet<>(valores);

        if (ods.stream().anyMatch(
                numero -> numero == null || numero < 1 || numero > 17)) {

            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Selecione somente ODS de 1 a 17.");
        }

        return ods;
    }

    // =====================================================
    // EXCLUSÃO DA OSC
    // CRIADOR E ADMINISTRADOR
    // =====================================================

    @Transactional
    public void delete(Integer oscId, Usuario usuario) {

        Osc osc = oscRepository.findForUpdateById(oscId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organização não encontrada."));

        exigirCargo(
                osc,
                usuario,
                CargoOsc.CRIADOR,
                CargoOsc.ADMINISTRADOR);

        limpezaService.excluirOscGerenciada(osc);
    }

    // =====================================================
    // ADICIONAR MEMBRO
    // CRIADOR E ADMINISTRADOR
    // =====================================================

    @Transactional
    public void addMembro(
            Integer oscId,
            Usuario usuario,
            RequestNewMembroDTO newMembroDTO) {

        Osc osc = oscRepository.findForUpdateById(oscId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organização não encontrada."));

        exigirCargo(
                osc,
                usuario,
                CargoOsc.CRIADOR,
                CargoOsc.ADMINISTRADOR);

        CargoOsc novoCargo = newMembroDTO.cargo();

        if (novoCargo == null || novoCargo == CargoOsc.CRIADOR) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O cargo informado não pode ser atribuído.");
        }

        Usuario novoMembro = usuarioRepository
                .findByEmail(new Email(newMembroDTO.email()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Usuário não encontrado."));

        if (novoMembro.getId().equals(usuario.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Você não pode atribuir um cargo a si mesmo.");
        }

        if (membrosRepository.findByOsc_IdAndUsuario_Id(oscId, novoMembro.getId()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Esse usuário já pertence a esta organização.");
        }

        Role role = roleRepository.findByNome("OSC")
                .orElseThrow(() -> new IllegalStateException(
                        "Role OSC não encontrada."));

        novoMembro.getRoles().add(role);

        OscMembros membroOsc = new OscMembros();
        membroOsc.setOsc(osc);
        membroOsc.setUsuario(novoMembro);
        membroOsc.inicializarId();
        membroOsc.setCargoOsc(novoCargo);
        membroOsc.setDataCriacao(LocalDateTime.now());

        membrosRepository.save(membroOsc);
    }

    // =====================================================
    // REMOVER MEMBRO
    // CRIADOR E ADMINISTRADOR
    // =====================================================

    @Transactional
    public void removeMembro(
            Integer oscId,
            Usuario usuario,
            Email email) {

        Osc osc = oscRepository.findForUpdateById(oscId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organização não encontrada."));

        exigirCargo(
                osc,
                usuario,
                CargoOsc.CRIADOR,
                CargoOsc.ADMINISTRADOR);

        OscMembros membroOsc = membrosRepository
                .findByOsc_IdAndUsuario_Email(oscId, email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Membro não encontrado nesta organização."));

        if (osc.getUsuario().getId()
                .equals(membroOsc.getUsuario().getId())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "O criador da organização não pode ser removido.");
        }

        CargoOsc cargoSolicitante = obterCargoUsuario(osc, usuario);
        if (membroOsc.getCargoOsc() == CargoOsc.ADMINISTRADOR
                && cargoSolicitante != CargoOsc.CRIADOR) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Somente o criador pode remover outro administrador.");
        }

        membrosRepository.delete(membroOsc);
    }

    @Transactional
    public void alterarCargoMembro(
            Integer oscId,
            Integer usuarioId,
            Usuario solicitante,
            CargoOsc novoCargo) {

        if (novoCargo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cargo obrigatório.");
        }

        Osc osc = oscRepository.findForUpdateById(oscId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Organização não encontrada."));
        CargoOsc cargoSolicitante = obterCargoUsuario(osc, solicitante);
        if (usuarioId.equals(solicitante.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Você não pode alterar o próprio cargo.");
        }

        OscMembros destino = membrosRepository.findByOsc_IdAndUsuario_Id(oscId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Membro não encontrado nesta organização."));

        if (novoCargo == CargoOsc.CRIADOR) {
            if (cargoSolicitante != CargoOsc.CRIADOR) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Somente o criador pode transferir o cargo de criador.");
            }
            if (destino.getCargoOsc() == CargoOsc.CRIADOR) {
                log.warn("Foi encontrado mais de um vínculo CRIADOR na OSC {}.", oscId);
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Os vínculos de criador desta organização estão inconsistentes.");
            }
            transferirCriador(osc, solicitante, destino);
            return;
        }

        exigirCargo(osc, solicitante, CargoOsc.CRIADOR, CargoOsc.ADMINISTRADOR);
        if (destino.getCargoOsc() == CargoOsc.CRIADOR) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "O criador atual só pode ser alterado pela transferência de titularidade.");
        }
        if (destino.getCargoOsc() == CargoOsc.ADMINISTRADOR
                && novoCargo != CargoOsc.ADMINISTRADOR
                && cargoSolicitante != CargoOsc.CRIADOR) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Somente o criador pode rebaixar outro administrador.");
        }

        destino.setCargoOsc(novoCargo);
        membrosRepository.save(destino);
    }

    private void transferirCriador(Osc osc, Usuario criadorAtual, OscMembros novoCriador) {
        OscMembros vinculoAtual = membrosRepository
                .findByOsc_IdAndUsuario_Id(osc.getId(), criadorAtual.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "O vínculo do criador desta organização está inconsistente."));

        if (!criadorAtual.getId().equals(osc.getUsuario().getId())
                || vinculoAtual.getCargoOsc() != CargoOsc.CRIADOR) {
            log.warn("Tentativa de transferir uma OSC com vínculo de criador inconsistente: {}.", osc.getId());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "O vínculo do criador desta organização está inconsistente.");
        }

        // A transferência mantém o titular anterior na OSC como administrador.
        vinculoAtual.setCargoOsc(CargoOsc.ADMINISTRADOR);
        novoCriador.setCargoOsc(CargoOsc.CRIADOR);
        osc.setUsuario(novoCriador.getUsuario());
        membrosRepository.save(vinculoAtual);
        membrosRepository.save(novoCriador);
        oscRepository.save(osc);
    }

    // =====================================================
    // CONSULTAS PÚBLICAS
    // =====================================================

    @Transactional(readOnly = true)
    public OscPerfilDTO buscarPerfilPublico(Integer id) {

        Osc osc = oscRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organização não encontrada."));

        return OscPerfilDTO.de(osc);
    }

    @Transactional(readOnly = true)
    public Page<OscListagemDTO> viewAll(Pageable pageable) {
        return oscRepository.findAll(pageable)
                .map(OscListagemDTO::de);
    }

    @Transactional(readOnly = true)
    public List<OscUsuarioDTO> listarDoUsuario(Usuario usuario) {

        if (usuario == null || usuario.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Autenticação necessária.");
        }

        List<OscMembros> vinculos = membrosRepository
                .findAllByUsuario_IdOrderByOsc_NomeAsc(usuario.getId());
        Set<Integer> oscsValidas = new java.util.HashSet<>();
        for (OscMembros vinculo : vinculos) {
            Osc osc = vinculo.getOsc();
            if (osc.getUsuario() == null || osc.getUsuario().getId() == null) {
                log.warn("A OSC {} não possui criador original válido.", osc.getId());
                continue;
            }
            boolean criadorOriginal = osc.getUsuario().getId().equals(usuario.getId());
            boolean consistente = criadorOriginal
                    ? vinculo.getCargoOsc() == CargoOsc.CRIADOR
                    : vinculo.getCargoOsc() != CargoOsc.CRIADOR;
            if (!consistente) {
                log.warn("Vínculo de membro inconsistente para a OSC {} e usuário {}.",
                        osc.getId(), usuario.getId());
                continue;
            }
            oscsValidas.add(osc.getId());
        }

        for (Osc osc : oscRepository.findAllByUsuario_IdOrderByNomeAsc(usuario.getId())) {
            boolean temVinculoCriador = vinculos.stream().anyMatch(vinculo ->
                    vinculo.getOsc().getId().equals(osc.getId())
                            && vinculo.getCargoOsc() == CargoOsc.CRIADOR);
            if (!temVinculoCriador) {
                log.warn("Criador sem vínculo CRIADOR na OSC {} para usuário {}.",
                        osc.getId(), usuario.getId());
                oscsValidas.remove(osc.getId());
            }
        }

        return vinculos.stream()
                .filter(vinculo -> oscsValidas.contains(vinculo.getOsc().getId()))
                .map(vinculo -> new OscUsuarioDTO(
                        vinculo.getOsc().getId(),
                        vinculo.getOsc().getNome(),
                        vinculo.getOsc().getLogo(),
                        vinculo.getCargoOsc()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OscMembroDTO> listarMembros(Integer oscId, Usuario solicitante) {
        Osc osc = oscRepository.findById(oscId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Organização não encontrada."));
        exigirCargo(osc, solicitante, CargoOsc.values());
        return membrosRepository.findAllByOsc_IdOrderByUsuario_NomeAsc(oscId)
                .stream()
                .map(OscMembroDTO::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public OscEdicaoDTO buscarDadosEdicao(Integer oscId, Usuario solicitante) {
        Osc osc = oscRepository.findById(oscId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Organização não encontrada."));
        exigirCargo(osc, solicitante, CargoOsc.values());
        return OscEdicaoDTO.de(osc);
    }
}
