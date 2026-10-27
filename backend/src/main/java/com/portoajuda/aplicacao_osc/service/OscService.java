package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestNewMembroDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestOscDTO;
import com.portoajuda.aplicacao_osc.dto.request.UpdateOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.dto.response.ConsultaCnpjDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.OscListagemDTO;
import com.portoajuda.aplicacao_osc.entity.*;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
// import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
// import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
// import io.jsonwebtoken.Jwts;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
// import java.util.List;
// import java.util.Set;

@Service
@RequiredArgsConstructor
public class OscService {
    private final OscRepository oscRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final OscMembrosRepository membrosRepository;
    private final GeocodingService geocodingService;
    private final ValidacaoCnpjOscService validacaoCnpjOscService;
    private final OscPersistenciaService persistenciaService;
    private final SupabaseStorageService supabaseStorageService;

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

    private void removerLogoSeNecessario(SupabaseStorageService.ArquivoEnviado logo) {
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

    @Transactional
    public void update(UpdateOscDTO updateOscDTO, Usuario usuario) throws AccessDeniedException {
        Osc osc = oscRepository.findByCnpj(new Cnpj(updateOscDTO.cnpj()))
                .orElseThrow(() -> new IllegalArgumentException("Cnpj não encontrado"));
        boolean isDono = oscRepository.existsByIdAndUsuarioId(osc.getId(), usuario.getId());
        boolean isMembro = oscRepository.userBelongsOsc(osc.getId(), usuario.getId());
        if(!isDono && !isMembro){
            throw new AccessDeniedException("Você não pode alterar os dados dessa ONG");
        }
        osc.setDescricao(updateOscDTO.descricao());
        osc.setEmail(new Email(updateOscDTO.email()));
        osc.setTelefone(updateOscDTO.telefone());
        osc.setSite(updateOscDTO.site());
        oscRepository.save(osc);
    }

    @Transactional
    public void delete(Usuario usuario) throws AccessDeniedException {
        Osc osc = oscRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Osc não encontrada"));
        boolean isDono = oscRepository.existsByIdAndUsuarioId(osc.getId(), usuario.getId());
        if(!isDono){
            throw new AccessDeniedException("Você não pode deletar essa ONG");
        }
        oscRepository.delete(osc);
    }

    @Transactional
    public void addMembro(Usuario usuario, RequestNewMembroDTO newMembroDTO) throws AccessDeniedException {
        Osc osc = oscRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Osc não encontrada"));
        boolean isDono = oscRepository.existsByIdAndUsuarioId(osc.getId(), usuario.getId());
        boolean isMembro = oscRepository.userBelongsOsc(osc.getId(), usuario.getId());
        if(!isDono && !isMembro){
            throw new AccessDeniedException("Você não pode adicionar membros nessa ONG");
        }

        Usuario novoMembro = usuarioRepository.findByEmail(new Email(newMembroDTO.email()))
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        if(!usuarioRepository.usuarioIsMember(novoMembro.getId())){
            throw new AccessDeniedException("Esse usuário já pertence a uma ONG");
        }
        Role role = roleRepository.findByNome("OSC")
                .orElseThrow(() -> new IllegalArgumentException("Role não encontrada"));
        novoMembro.getRoles().add(role);
        OscMembros membroOsc = new OscMembros();
        membroOsc.setOsc(osc);
        membroOsc.setUsuario(novoMembro);
        membroOsc.setCargoOsc(newMembroDTO.cargo());
        membroOsc.setDataCriacao(LocalDateTime.now());
        membrosRepository.save(membroOsc);
    }

    @Transactional
    public void removeMembro(Usuario usuario, Email email) throws AccessDeniedException {
        Osc osc = oscRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Osc não encontrada"));
        boolean isDono = oscRepository.existsByIdAndUsuarioId(osc.getId(), usuario.getId());
        boolean isMembro = oscRepository.userBelongsOsc(osc.getId(), usuario.getId());
        if(!isDono && !isMembro){
            throw new AccessDeniedException("Você não pertence à essa ONG");
        }
        OscMembros membroOsc = membrosRepository.findByUsuarioEmail(email)
                        .orElseThrow(() -> new IllegalArgumentException("Usuário Membro não encontrado"));
        if(!membroOsc.getOsc().getId().equals(osc.getId())){
            throw new AccessDeniedException("Erro ao buscar membro nessa ONG");
        }

        membrosRepository.delete(membroOsc);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<OscListagemDTO> viewAll(Pageable pageable){
        return oscRepository.findAll(pageable).map(OscListagemDTO::de);
    }
}
