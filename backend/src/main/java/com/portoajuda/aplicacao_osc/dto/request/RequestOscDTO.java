package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.br.CNPJ;

import java.time.LocalTime;
import java.util.Set;

public record RequestOscDTO(
        @NotBlank(message = "CNPJ é obrigatório")
        @CNPJ(message = "CNPJ inválido")
        String cnpj,

        CausaSocial causaPrincipal,

        Set<CausaSocial> causasSecundarias,

        /** Compatibilidade temporária com clientes que ainda enviam a lista achatada. */
        Set<CausaSocial> causas,

        Set<Integer> ods,

        @NotBlank(message = "Nome da organização é obrigatório")
        @Size(max = 255)
        String nome,

        @NotBlank(message = "Descrição curta é obrigatória")
        @Size(max = 200)
        String descricaoCurta,

        @NotBlank(message = "Descrição completa é obrigatória")
        @Size(max = 10000)
        String descricao,

        @NotNull(message = "Ano de fundação é obrigatório")
        @Min(1800)
        @Max(2100)
        Integer anoFundacao,

        @NotBlank(message = "E-mail institucional é obrigatório")
        @Email(message = "E-mail institucional inválido")
        String email,

        @Size(max = 15)
        String telefone,
        @Size(max = 20)
        String whatsapp,

        @URL(message = "URL do site inválida")
        @Size(max = 255)
        String site,

        @URL(message = "URL do LinkedIn inválida")
        @Size(max = 500)
        String linkedin,

        @URL(message = "URL do Instagram inválida")
        @Size(max = 500)
        String instagram,

        @URL(message = "URL do YouTube inválida")
        @Size(max = 500)
        String youtube,

        @NotBlank(message = "Selecione a área geográfica")
        @Size(max = 30)
        String areaGeografica,

        @NotBlank(message = "País é obrigatório")
        @Size(max = 100)
        String pais,

        @NotBlank(message = "Estado é obrigatório")
        @Size(max = 100)
        String estado,

        @NotBlank(message = "Cidade é obrigatória")
        @Size(max = 100)
        String cidade,

        @NotBlank(message = "CEP é obrigatório")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido")
        String cep,

        @NotBlank(message = "Bairro é obrigatório")
        @Size(max = 100)
        String bairro,

        @NotBlank(message = "Rua é obrigatória")
        @Size(max = 100)
        String rua,

        @NotBlank(message = "Número é obrigatório")
        @Size(max = 100)
        String numero,

        @Size(max = 100)
        String complemento,
        Set<String> diasFuncionamento,
        LocalTime horarioAbertura,
        LocalTime horarioFechamento,
        @NotNull(message = "Informe se há atendimento presencial")
        Boolean atendimentoPresencial,
        @NotNull(message = "Informe se há atendimento online")
        Boolean atendimentoOnline,
        Boolean necessitaAgendamento,
        @NotNull(message = "Informe se aceita voluntários")
        Boolean aceitaVoluntarios,
        String frequenciaVoluntariado,
        @Min(0) @Max(99)
        Integer idadeMinimaVoluntario,
        Boolean processoSeletivoVoluntario,
        Boolean emiteCertificadoVoluntario,
        @Size(max = 10000)
        String atividadesVoluntariado
) {
}
