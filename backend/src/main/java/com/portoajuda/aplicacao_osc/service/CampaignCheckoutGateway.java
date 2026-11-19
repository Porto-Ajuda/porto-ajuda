package com.portoajuda.aplicacao_osc.service;

public interface CampaignCheckoutGateway {
    SessionResult create(SessionCommand command);
    record SessionCommand(String connectedAccountId,String quoteId,long contributionId,long campaignId,
            int donorId,String ngoName,long totalCents,long platformFeeCents,String method) { }
    record SessionResult(String sessionId,String paymentIntentId,String url) { }
}
