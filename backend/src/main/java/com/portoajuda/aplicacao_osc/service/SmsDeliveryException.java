package com.portoajuda.aplicacao_osc.service;

/** Falha sanitizada de envio SMS; não inclui token, conteúdo ou corpo do provedor. */
public final class SmsDeliveryException extends RuntimeException {

    public enum Reason {
        CONFIGURATION,
        INVALID_REQUEST,
        PROVIDER_REJECTED,
        PROVIDER_UNAVAILABLE,
        TIMEOUT,
        INVALID_RESPONSE
    }

    private final Reason reason;
    private final Integer providerStatus;

    private SmsDeliveryException(Reason reason, Integer providerStatus, String message) {
        super(message);
        this.reason = reason;
        this.providerStatus = providerStatus;
    }

    static SmsDeliveryException configuration(String message) {
        return new SmsDeliveryException(Reason.CONFIGURATION, null, message);
    }

    static SmsDeliveryException invalidRequest(String message) {
        return new SmsDeliveryException(Reason.INVALID_REQUEST, null, message);
    }

    static SmsDeliveryException providerRejected(int status) {
        return new SmsDeliveryException(
                Reason.PROVIDER_REJECTED,
                status,
                "SMS Mais rejeitou a solicitação (HTTP " + status + ").");
    }

    static SmsDeliveryException providerUnavailable() {
        return new SmsDeliveryException(
                Reason.PROVIDER_UNAVAILABLE,
                null,
                "Não foi possível obter resposta do serviço SMS Mais.");
    }

    static SmsDeliveryException timeout() {
        return new SmsDeliveryException(
                Reason.TIMEOUT,
                null,
                "O tempo limite de comunicação com SMS Mais foi excedido; o resultado do envio é desconhecido.");
    }

    static SmsDeliveryException invalidResponse() {
        return new SmsDeliveryException(
                Reason.INVALID_RESPONSE,
                null,
                "SMS Mais retornou uma resposta de envio inválida.");
    }

    public Reason getReason() {
        return reason;
    }

    public Integer getProviderStatus() {
        return providerStatus;
    }
}
