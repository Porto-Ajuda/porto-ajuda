package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;

import java.time.LocalTime;
import java.util.Set;

/** Snapshot de edição, sem anotações de validação próprias do contrato de escrita. */
public record OscEdicaoDTO(
        String cnpj, String nome, String descricaoCurta, String descricao, Integer anoFundacao,
        String email, String telefone, String whatsapp, String site, String linkedin,
        String instagram, String youtube, String areaGeografica, String pais, String estado,
        String cidade, String cep, String bairro, String rua, String numero, String complemento,
        String diasHorariosFuncionamento, LocalTime horarioAbertura, LocalTime horarioFechamento,
        Boolean atendimentoPresencial, Boolean atendimentoOnline, Boolean necessitaAgendamento,
        Boolean aceitaVoluntarios, String frequenciaVoluntariado, Integer idadeMinimaVoluntario,
        Boolean processoSeletivoVoluntario, Boolean emiteCertificadoVoluntario,
        String atividadesVoluntariado, CausaSocial causaPrincipal,
        Set<CausaSocial> causasSecundarias, Set<Integer> ods, String logo
) {
    public static OscEdicaoDTO de(Osc osc) {
        return new OscEdicaoDTO(
                osc.getCnpj() == null ? null : osc.getCnpj().getCnpj(), osc.getNome(), osc.getDescricaoCurta(), osc.getDescricao(),
                osc.getAnoFundacao(), osc.getEmail() == null ? null : osc.getEmail().valor(), osc.getTelefone(), osc.getWhatsapp(),
                osc.getSite(), osc.getLinkedin(), osc.getInstagram(), osc.getYoutube(),
                osc.getAreaGeografica(), osc.getPais(), osc.getEstado(), osc.getCidade(), osc.getCep(),
                osc.getBairro(), osc.getRua(), osc.getNumero(), osc.getComplemento(),
                osc.getDiasHorariosFuncionamento(),
                osc.getHorarioAbertura(), osc.getHorarioFechamento(), osc.getAtendimentoPresencial(),
                osc.getAtendimentoOnline(), osc.getNecessitaAgendamento(), osc.getAceitaVoluntarios(),
                osc.getFrequenciaVoluntariado(), osc.getIdadeMinimaVoluntario(),
                osc.getProcessoSeletivoVoluntario(), osc.getEmiteCertificadoVoluntario(),
                osc.getAtividadesVoluntariado(), osc.getCausaPrincipal(),
                copiar(osc.getCausasSecundarias()), copiar(osc.getOds()), osc.getLogo());
    }

    private static <T> Set<T> copiar(Set<T> valores) {
        return valores == null ? Set.of() : Set.copyOf(valores);
    }
}
