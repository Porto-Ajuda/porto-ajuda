
package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;

import java.time.LocalTime;
import java.util.Set;

public record OscPerfilDTO(
        Integer id,
        String nome,
        String logo,
        String descricaoCurta,
        String descricao,
        Integer anoFundacao,
        CausaSocial causaPrincipal,
        Set<CausaSocial> causasSecundarias,
        /** Compatibilidade para consumidores antigos da lista plana. */
        Set<CausaSocial> causas,
        Set<Integer> ods,
        /** E-mail cadastrado como contato institucional pela OSC. */
        String email,
        String rua,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado,
        String pais,
        String cep,
        double latitude,
        double longitude,
        String telefone,
        String whatsapp,
        String instagram,
        String site,
        String linkedin,
        String youtube,
        String areaGeografica,
        String diasHorariosFuncionamento,
        LocalTime horarioAbertura,
        LocalTime horarioFechamento,
        Boolean atendimentoPresencial,
        Boolean atendimentoOnline,
        Boolean necessitaAgendamento,
        Boolean aceitaVoluntarios,
        String frequenciaVoluntariado,
        Integer idadeMinimaVoluntario,
        Boolean processoSeletivoVoluntario,
        Boolean emiteCertificadoVoluntario,
        String atividadesVoluntariado
) {
    public static OscPerfilDTO de(Osc osc) {
        return new OscPerfilDTO(
                osc.getId(),
                osc.getNome(),
                osc.getLogo(),
                osc.getDescricaoCurta(),
                osc.getDescricao(),
                osc.getAnoFundacao(),
                osc.getCausaPrincipal(),
                Set.copyOf(osc.getCausasSecundarias()),
                Set.copyOf(osc.getCausas()),
                Set.copyOf(osc.getOds()),
                osc.getEmail() == null ? null : osc.getEmail().valor(),
                osc.getRua(),
                osc.getNumero(),
                osc.getComplemento(),
                osc.getBairro(),
                osc.getCidade(),
                osc.getEstado(),
                osc.getPais(),
                osc.getCep(),
                osc.getLatitude(),
                osc.getLongitude(),
                osc.getTelefone(),
                osc.getWhatsapp(),
                osc.getInstagram(),
                osc.getSite(),
                osc.getLinkedin(),
                osc.getYoutube(),
                osc.getAreaGeografica(),
                osc.getDiasHorariosFuncionamento(),
                osc.getHorarioAbertura(),
                osc.getHorarioFechamento(),
                osc.getAtendimentoPresencial(),
                osc.getAtendimentoOnline(),
                osc.getNecessitaAgendamento(),
                osc.getAceitaVoluntarios(),
                osc.getFrequenciaVoluntariado(),
                osc.getIdadeMinimaVoluntario(),
                osc.getProcessoSeletivoVoluntario(),
                osc.getEmiteCertificadoVoluntario(),
                osc.getAtividadesVoluntariado()
        );
    }
}
