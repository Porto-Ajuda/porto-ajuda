package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.CandidaturasVoluntariado;
import com.portoajuda.aplicacao_osc.enums.ModalidadeVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import java.time.LocalDate;

public record CandidaturaUsuarioDTO(
        Integer id,
        Integer vagaId,
        Integer postId,
        Integer oscId,
        String tituloOportunidade,
        String nomeOsc,
        LocalDate dataCandidatura,
        StatusCandidatura status,
        String mensagem,
        ModalidadeVoluntariado modalidade,
        String cidade,
        String bairro,
        LocalDate dataInicio,
        LocalDate dataFim,
        StatusVaga statusVaga,
        boolean oportunidadePublica) {

    public static CandidaturaUsuarioDTO from(CandidaturasVoluntariado candidatura) {
        var vaga = candidatura.getVaga();
        var post = vaga.getPost();
        var osc = post.getOsc();
        boolean publica = post.isAtivo() && osc.getStatus() == com.portoajuda.aplicacao_osc.enums.StatusOsc.ATIVA
                && !osc.isArquivada() && vaga.getStatus() == StatusVaga.ABERTA
                && vaga.getQuantidadeVagas() != null && vaga.getQuantidadeVagas() > 0
                && vaga.getDataFim() != null && !vaga.getDataFim().isBefore(LocalDate.now());
        return new CandidaturaUsuarioDTO(candidatura.getId(), vaga.getId(), post.getId(), osc.getId(),
                post.getTitulo(), osc.getNome(), candidatura.getDataCandidatura(), candidatura.getStatus(),
                candidatura.getMensagem(), vaga.getModalidade(), vaga.getCidade(), vaga.getBairro(),
                vaga.getDataInicio(), vaga.getDataFim(), vaga.getStatus(), publica);
    }
}
