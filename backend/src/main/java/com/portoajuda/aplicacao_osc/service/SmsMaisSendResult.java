package com.portoajuda.aplicacao_osc.service;

/** Confirma que a SMS Mais aceitou o envio; não representa confirmação de entrega. */
public final class SmsMaisSendResult {
    private final String providerMessageId;
    private final String externalId;
    private final String messageEcho;

    public SmsMaisSendResult(String providerMessageId, String externalId) {
        this(providerMessageId, externalId, null);
    }

    public SmsMaisSendResult(String providerMessageId, String externalId, String messageEcho) {
        this.providerMessageId = providerMessageId;
        this.externalId = externalId;
        this.messageEcho = messageEcho;
    }

    public String providerMessageId() { return providerMessageId; }
    public String externalId() { return externalId; }
    public String messageEcho() { return messageEcho; }

    @Override
    public String toString() {
        return "SmsMaisSendResult[providerMessageId=" + providerMessageId + ", externalId=" + externalId
                + ", messageEcho=[REDACTED]]";
    }
}
