package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.Vaga;
import com.portoajuda.aplicacao_osc.enums.ModalidadeVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusVaga;
import java.time.LocalDate;

/** Leitura da oportunidade associada a um post, sem expor candidaturas. */
public record VagaConteudoDTO(
        Integer id,
        Integer quantidadeVagas,
        ModalidadeVoluntariado modalidadeVoluntariado,
        String requisitos,
        String cargaHoraria,
        String cidade,
        String bairro,
        String rua,
        String numero,
        String complemento,
        LocalDate dataInicio,
        LocalDate dataFim,
        StatusVaga status) {

    public static VagaConteudoDTO from(Vaga vaga) {
        if (vaga == null) {
            return null;
        }
        return new VagaConteudoDTO(
                vaga.getId(), vaga.getQuantidadeVagas(), vaga.getModalidade(),
                vaga.getRequisitos(), vaga.getCargaHoraria(), vaga.getCidade(),
                vaga.getBairro(), vaga.getRua(), vaga.getNumero(), vaga.getComplemento(),
                vaga.getDataInicio(), vaga.getDataFim(), vaga.getStatus());
    }
}
