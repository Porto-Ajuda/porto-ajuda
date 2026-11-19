package com.portoajuda.aplicacao_osc.service;

public interface CampaignStripeLookup {
    StripeChargeDetails charge(String chargeId,String connectedAccountId);
    Long balanceFeeCents(String balanceTransactionId,String connectedAccountId);
    record StripeChargeDetails(String paymentIntentId,String balanceTransactionId,Long amountCents,
            Long refundedCents,String currency,boolean paid) { }
}
