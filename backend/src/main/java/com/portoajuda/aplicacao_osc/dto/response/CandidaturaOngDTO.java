package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.CandidaturasVoluntariado;
import com.portoajuda.aplicacao_osc.enums.StatusCandidatura;
import java.time.LocalDate;
import java.math.BigDecimal;

/** Dados mínimos de candidatos, visíveis somente a membros autorizados da ONG. */
public record CandidaturaOngDTO(
        Integer id,
        Integer vagaId,
        Integer postId,
        String tituloOportunidade,
        String nomeCandidato,
        String emailCandidato,
        LocalDate dataCandidatura,
        StatusCandidatura status,
        String mensagem,
        LocalDate dataConclusao,
        BigDecimal horasRealizadas,
        boolean certificadoDisponivel,
        boolean podeDecidir) {

    public static CandidaturaOngDTO from(CandidaturasVoluntariado candidatura, boolean podeDecidir) {
        var usuario = candidatura.getUsuario();
        String nome = usuario.getNomeSocial() == null || usuario.getNomeSocial().isBlank()
                ? usuario.getNome() : usuario.getNomeSocial();
        return new CandidaturaOngDTO(candidatura.getId(), candidatura.getVaga().getId(),
                candidatura.getVaga().getPost().getId(), candidatura.getVaga().getPost().getTitulo(),
                nome, usuario.getEmail() == null ? null : usuario.getEmail().valor(),
                candidatura.getDataCandidatura(), candidatura.getStatus(), candidatura.getMensagem(),
                candidatura.getDataConclusao(), candidatura.getHorasRealizadas(),
                candidatura.getCertificadoObjectKey() != null, podeDecidir);
    }
}
