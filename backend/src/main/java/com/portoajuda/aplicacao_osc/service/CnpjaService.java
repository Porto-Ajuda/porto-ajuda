
package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.utils.Cnpj;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class CnpjaService {

    private final RestClient restClient;

    public CnpjaService(@Value("${cnpja.url-base:https://open.cnpja.com/office/}") String urlBase) {
        String normalizedBase = urlBase.endsWith("/") ? urlBase : urlBase + "/";
        this.restClient = RestClient.create(normalizedBase);
    }

    public JsonNode consultarCnpj(String cnpj) {
        String cnpjCanonico = new Cnpj(cnpj).getCnpj();

        try {
            JsonNode resposta = restClient.get()
                    .uri("{cnpj}", cnpjCanonico)
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
