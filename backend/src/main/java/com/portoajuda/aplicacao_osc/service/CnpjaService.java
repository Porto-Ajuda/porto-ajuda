
package com.portoajuda.aplicacao_osc.service;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class CnpjaService {

    private static final String URL_BASE = "https://open.cnpja.com/office/";

    private final RestClient restClient;

    public CnpjaService() {
        this.restClient = RestClient.create(URL_BASE);
    }

    public JsonNode consultarCnpj(String cnpj) {
        String cnpjLimpo = cnpj.replaceAll("\\D", "");

        if (!cnpjLimpo.matches("\\d{14}")) {
            throw new IllegalArgumentException(
                    "O CNPJ deve conter 14 dígitos.");
        }

        try {
            JsonNode resposta = restClient.get()
                    .uri(cnpjLimpo)
                    .retrieve()
                    .body(JsonNode.class);

            if (resposta == null || resposta.isEmpty()) {
                throw new IllegalStateException(
                        "A consulta não retornou dados.");
            }

            return resposta;

        } catch (RestClientException e) {
            throw new IllegalStateException(
                    "Não foi possível consultar o CNPJ. "
                            + "Tente novamente mais tarde.",
                    e);
        }
    }
}
