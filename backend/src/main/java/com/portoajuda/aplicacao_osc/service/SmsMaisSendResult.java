package com.portoajuda.aplicacao_osc.service;

/** Confirma que a SMS Mais aceitou o envio; não representa confirmação de entrega. */
public record SmsMaisSendResult(String providerMessageId, String externalId) {
}
