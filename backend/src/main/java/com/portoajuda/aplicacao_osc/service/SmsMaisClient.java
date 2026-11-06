package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.config.SmsMaisProperties;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** Cliente dedicado ao endpoint REST oficial da SMS Mais. */
@Component
public class SmsMaisClient {

    private static final String SANDBOX_TOKEN_PREFIX = "sbx_";

    private final RestClient restClient;
    private final SmsMaisProperties properties;

    public SmsMaisClient(
            @Qualifier("smsMaisRestClient") RestClient restClient,
            SmsMaisProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public SmsMaisSendResult enviar(String telefone, String mensagem, String externalId) {
        validarConfiguracao();
        validarRequisicao(telefone, mensagem, externalId);

        var payload = new SendRequest(List.of(
                new SendMessage(externalId, List.of(new Destination(telefone)), mensagem)));

        try {
            JsonNode resposta = restClient.post()
                    .uri("/send")
                    .header("Authorization", "Bearer " + properties.getToken().trim())
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw SmsDeliveryException.providerRejected(response.getStatusCode().value());
                    })
                    .body(JsonNode.class);

            return interpretarResposta(resposta, externalId);
        } catch (SmsDeliveryException erro) {
            throw erro;
        } catch (ResourceAccessException erro) {
            if (contemTimeout(erro)) {
                throw SmsDeliveryException.timeout();
            }
            throw SmsDeliveryException.providerUnavailable();
        } catch (HttpMessageConversionException erro) {
            throw SmsDeliveryException.invalidResponse();
        } catch (RestClientException erro) {
            if (contemErroConversao(erro)) {
                throw SmsDeliveryException.invalidResponse();
            }
            throw SmsDeliveryException.providerUnavailable();
        } catch (IllegalStateException erro) {
            throw SmsDeliveryException.providerUnavailable();
        }
    }

    private void validarConfiguracao() {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            throw SmsDeliveryException.configuration(
                    "SMS Mais não está configurado: defina SMSMAIS_TOKEN no backend.");
        }

        String environment = properties.getEnvironment();
        if (environment == null || environment.isBlank()) {
            throw SmsDeliveryException.configuration(
                    "SMS Mais não está configurado: defina SMSMAIS_ENVIRONMENT como sandbox ou production.");
        }

        String normalizedEnvironment = environment.trim().toLowerCase(Locale.ROOT);
        boolean sandboxToken = token.trim().startsWith(SANDBOX_TOKEN_PREFIX);
        if (normalizedEnvironment.equals("sandbox") && !sandboxToken) {
            throw SmsDeliveryException.configuration(
                    "SMS Mais em sandbox exige token de homologação com prefixo sbx_.");
        }
        if (normalizedEnvironment.equals("production") && sandboxToken) {
            throw SmsDeliveryException.configuration(
                    "SMS Mais em production não aceita token de homologação sbx_.");
        }
        if (!normalizedEnvironment.equals("sandbox") && !normalizedEnvironment.equals("production")) {
            throw SmsDeliveryException.configuration(
                    "SMSMAIS_ENVIRONMENT deve ser sandbox ou production.");
        }
    }

    private void validarRequisicao(String telefone, String mensagem, String externalId) {
        if (telefone == null || !telefone.matches("55[1-9][0-9]9[0-9]{8}")) {
            throw SmsDeliveryException.invalidRequest(
                    "O destinatário deve estar normalizado como DDI 55, DDD e número nacional.");
        }
        if (mensagem == null || mensagem.isBlank() || mensagem.codePointCount(0, mensagem.length()) > 160) {
            throw SmsDeliveryException.invalidRequest(
                    "A mensagem SMS deve conter de 1 a 160 caracteres.");
        }
        if (externalId == null || externalId.isBlank()) {
            throw SmsDeliveryException.invalidRequest(
                    "O identificador da solicitação SMS é obrigatório.");
        }
    }

    private SmsMaisSendResult interpretarResposta(JsonNode resposta, String externalId) {
        if (resposta == null || !resposta.isArray()) {
            throw SmsDeliveryException.invalidResponse();
        }

        for (JsonNode item : resposta) {
            String respostaExternalId = texto(item, "externalId");
            String providerId = texto(item, "id");
            if (Objects.equals(externalId, respostaExternalId)
                    && providerId != null
                    && !providerId.isBlank()) {
                return new SmsMaisSendResult(providerId, respostaExternalId);
            }
        }
        throw SmsDeliveryException.invalidResponse();
    }

    private String texto(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.isArray() || value.isObject()) {
            return null;
        }
        return value.asText();
    }

    private boolean contemTimeout(Throwable erro) {
        Throwable atual = erro;
        while (atual != null) {
            if (atual instanceof SocketTimeoutException
                    || atual instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }

    private boolean contemErroConversao(Throwable erro) {
        Throwable atual = erro;
        while (atual != null) {
            if (atual instanceof HttpMessageConversionException) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }

    private record SendRequest(List<SendMessage> messages) {
    }

    private record SendMessage(String id, List<Destination> destinations, String msg) {
    }

    private record Destination(String to) {
    }
}
