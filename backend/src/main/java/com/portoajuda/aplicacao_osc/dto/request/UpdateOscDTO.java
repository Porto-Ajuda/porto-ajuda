package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.br.CNPJ;

import java.time.LocalTime;
import java.util.Set;

/** Campos que podem ser alterados sem trocar a identidade ou o criador da OSC. */
public record UpdateOscDTO(
        @NotBlank @CNPJ(message = "CNPJ inválido") String cnpj,
        @Size(max = 255) String nome,
        @NotBlank(message = "Descrição curta é obrigatória") @Size(max = 200) String descricaoCurta,
        @NotBlank(message = "Descrição é obrigatória") @Size(max = 10000) String descricao,
        @Min(1800) @Max(2100) Integer anoFundacao,
        @NotBlank(message = "E-mail é obrigatório") @Email String email,
        @Size(max = 15) String telefone,
        @Size(max = 20) String whatsapp,
        @URL @Size(max = 255) String site,
        @URL @Size(max = 500) String linkedin,
        @URL @Size(max = 500) String instagram,
        @URL @Size(max = 500) String youtube,
        @NotBlank @Size(max = 30) String areaGeografica,
        @NotBlank @Size(max = 100) String pais,
        @Size(max = 100) String estado,
        @NotBlank @Size(max = 100) String cidade,
        @NotBlank @Pattern(regexp = "^\\d{5}-?\\d{3}$") @Size(max = 9) String cep,
        @NotBlank @Size(max = 100) String bairro,
        @NotBlank @Size(max = 100) String rua,
        @Size(max = 100) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 100) String diasHorariosFuncionamento,
        LocalTime horarioAbertura,
        LocalTime horarioFechamento,
        Boolean atendimentoPresencial,
        Boolean atendimentoOnline,
        Boolean necessitaAgendamento,
        Boolean aceitaVoluntarios,
        String frequenciaVoluntariado,
        @Min(0) @Max(99) Integer idadeMinimaVoluntario,
        Boolean processoSeletivoVoluntario,
        Boolean emiteCertificadoVoluntario,
        @Size(max = 10000) String atividadesVoluntariado,
        CausaSocial causaPrincipal,
        Set<CausaSocial> causasSecundarias,
        Set<Integer> ods
) {
    /** Mantém compatibilidade dos testes e clientes que enviavam somente os campos antigos. */
    public UpdateOscDTO(String cnpj, String descricao, String email, String telefone, String site,
            CausaSocial causaPrincipal, Set<CausaSocial> causasSecundarias, Set<Integer> ods) {
        this(cnpj,
                null, null, descricao, null, email, telefone, null, site,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, causaPrincipal, causasSecundarias, ods);
    }
}
