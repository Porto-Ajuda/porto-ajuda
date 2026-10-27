package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestOscDTO;
import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.dto.response.ResponseOscDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Service
@RequiredArgsConstructor
public class OscPersistenciaService {

    private static final Set<String> AREAS_GEOGRAFICAS = Set.of(
            "bairro", "cidade", "municipal", "regional", "estadual", "nacional", "internacional");
    private static final Set<String> DIAS_SEMANA = Set.of(
            "segunda", "terca", "quarta", "quinta", "sexta", "sabado", "domingo");

    private final OscRepository oscRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public ResponseOscDTO salvar(
            RequestOscDTO dados,
            Integer usuarioId,
            Coordenadas coordenadas,
            String logoUrl) {

        validarValoresDeDominio(dados, coordenadas);
        Cnpj cnpj = new Cnpj(dados.cnpj());
        if (oscRepository.existsByCnpj(cnpj)) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Já existe uma organização cadastrada com este CNPJ.");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "A sessão do usuário não é válida."));

        if (!usuario.isEnabled() || !usuario.isEmailVerificado()) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "É necessário ter uma conta ativa e com e-mail confirmado para cadastrar uma OSC.");
        }

        Role roleOsc = roleRepository.findByNome("OSC")
                .orElseThrow(() -> new IllegalStateException(
                        "A configuração de papéis do sistema está incompleta."));

        Osc osc = new Osc();
        osc.setCnpj(cnpj);
        osc.setCausas(new HashSet<>(dados.causas()));
        osc.setOds(validarOds(dados.ods()));
        osc.setUsuario(usuario);
        osc.setNome(dados.nome().trim());
        osc.setDescricaoCurta(dados.descricaoCurta().trim());
        osc.setAnoFundacao(dados.anoFundacao());
        osc.setLogo(logoUrl);
        osc.setDescricao(dados.descricao().trim());
        osc.setEmail(new Email(dados.email().trim()));
        osc.setTelefone(limparOpcional(dados.telefone()));
        osc.setWhatsapp(limparOpcional(dados.whatsapp()));
        osc.setInstagram(limparOpcional(dados.instagram()));
        osc.setSite(limparOpcional(dados.site()));
        osc.setLinkedin(limparOpcional(dados.linkedin()));
        osc.setYoutube(limparOpcional(dados.youtube()));
        osc.setAreaGeografica(normalizarArea(dados.areaGeografica()));
        osc.setPais(dados.pais().trim());
        osc.setEstado(dados.estado().trim());
        osc.setCidade(dados.cidade().trim());
        osc.setCep(dados.cep().trim());
        osc.setBairro(dados.bairro().trim());
        osc.setRua(dados.rua().trim());
        osc.setNumero(dados.numero().trim());
        osc.setComplemento(limparOpcional(dados.complemento()));
        osc.setDiasHorariosFuncionamento(montarResumoFuncionamento(dados));
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
        osc.setLatitude(coordenadas.latitude());
        osc.setLongitude(coordenadas.longitude());

        usuario.getRoles().add(roleOsc);
        Osc salva = oscRepository.save(osc);

        return new ResponseOscDTO(
                salva.getId(),
                salva.getCnpj().getCnpj(),
                salva.getNome(),
                Set.copyOf(salva.getCausas()),
                Set.copyOf(salva.getOds()));
    }

    private void validarValoresDeDominio(RequestOscDTO dados, Coordenadas coordenadas) {
        if (dados.anoFundacao() > LocalDate.now().getYear()) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "O ano de fundação não pode estar no futuro.");
        }

        if (dados.horarioAbertura() != null
                && dados.horarioFechamento() != null
                && !dados.horarioFechamento().isAfter(dados.horarioAbertura())) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "O horário de fechamento deve ser posterior ao de abertura.");
        }

        if ((dados.horarioAbertura() == null) != (dados.horarioFechamento() == null)) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "Informe os horários de abertura e fechamento juntos.");
        }

        if (!AREAS_GEOGRAFICAS.contains(normalizarArea(dados.areaGeografica()))) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "A área geográfica selecionada é inválida.");
        }

        if (dados.diasFuncionamento() != null
                && !DIAS_SEMANA.containsAll(dados.diasFuncionamento())) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "Um ou mais dias de funcionamento são inválidos.");
        }

        validarOds(dados.ods());
        if (coordenadas == null
                || !Double.isFinite(coordenadas.latitude())
                || coordenadas.latitude() < -90
                || coordenadas.latitude() > 90
                || !Double.isFinite(coordenadas.longitude())
                || coordenadas.longitude() < -180
                || coordenadas.longitude() > 180) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "Não foi possível confirmar a localização do endereço.");
        }
    }

    private Set<Integer> validarOds(Set<Integer> valores) {
        Set<Integer> resultado = valores == null ? Set.of() : new HashSet<>(valores);
        if (resultado.stream().anyMatch(ods -> ods == null || ods < 1 || ods > 17)) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "Selecione somente ODS de 1 a 17.");
        }
        return resultado;
    }

    private String normalizarArea(String area) {
        return area == null ? "" : area.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String limparOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private String montarResumoFuncionamento(RequestOscDTO dados) {
        String dias = dados.diasFuncionamento() == null
                ? ""
                : dados.diasFuncionamento().stream().sorted().collect(java.util.stream.Collectors.joining(", "));
        if (dados.horarioAbertura() == null) {
            return dias.isBlank() ? null : dias;
        }
        return dias + " " + dados.horarioAbertura() + "-" + dados.horarioFechamento();
    }
}
