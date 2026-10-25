
package com.portoajuda.aplicacao_osc.service;

import tools.jackson.databind.JsonNode;
import com.portoajuda.aplicacao_osc.dto.response.ConsultaCnpjDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
public class ValidacaoCnpjOscService {

    private final CnpjaService cnpjaService;

    public ValidacaoCnpjOscService(CnpjaService cnpjaService) {
        this.cnpjaService = cnpjaService;
    }

    public ConsultaCnpjDTO validar(String cnpj) {
        JsonNode dados = cnpjaService.consultarCnpj(cnpj);

        String cnpjConsultado = texto(dados, "taxId");
        String razaoSocial = texto(
                dados.path("company"), "name");
        String nomeFantasia = texto(dados, "alias");
        String dataAbertura = texto(dados, "founded");
        String situacao = texto(
                dados.path("status"), "text");
        String natureza = texto(
                dados.path("company").path("nature"), "text");

        String motivo = verificarElegibilidade(
                dados, dataAbertura, situacao, natureza);

        boolean elegivel = motivo == null;

        return new ConsultaCnpjDTO(
                cnpjConsultado,
                razaoSocial,
                nomeFantasia,
                dataAbertura,
                situacao,
                natureza,
                elegivel,
                elegivel
                        ? "CNPJ aprovado na verificação inicial."
                        : motivo);
    }

    private String verificarElegibilidade(
            JsonNode dados,
            String dataAbertura,
            String situacao,
            String natureza) {

        if (dados.path("taxId").asText().isBlank()) {
            return "A consulta não retornou um CNPJ válido.";
        }

        if (situacao == null
                || !situacao.equalsIgnoreCase("Ativa")) {
            return "O CNPJ precisa estar com situação cadastral ativa.";
        }

        if (dataAbertura == null) {
            return "Não foi possível confirmar a data de abertura.";
        }

        try {
            LocalDate abertura = LocalDate.parse(dataAbertura);

            if (abertura.isAfter(LocalDate.now().minusYears(1))) {
                return "A organização precisa ter pelo menos um ano de existência.";
            }
        } catch (DateTimeParseException e) {
            return "A data de abertura retornada não pôde ser validada.";
        }

        if (natureza == null) {
            return "Não foi possível confirmar a natureza jurídica.";
        }

        String codigoNatureza = dados.path("company")
                .path("nature")
                .path("id")
                .asText("");

        /*
         * Esta comparação depende do formato de company.nature.id
         * retornado pela API. Os códigos devem ser confirmados
         * antes de liberar cadastros em produção.
         */
        if (!codigoNatureza.equals("399-9")
                && !codigoNatureza.equals("306-9")
                && !codigoNatureza.equals("399")
                && !codigoNatureza.equals("306")) {
            return "A natureza jurídica não está entre as categorias aceitas para cadastro automático.";
        }

        return null;
    }

    private String texto(JsonNode objeto, String campo) {
        JsonNode valor = objeto.path(campo);

        if (valor.isMissingNode()
                || valor.isNull()
                || valor.asText().isBlank()) {
            return null;
        }

        return valor.asText();
    }
}
