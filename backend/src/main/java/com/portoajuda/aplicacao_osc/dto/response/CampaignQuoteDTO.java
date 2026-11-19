package com.portoajuda.aplicacao_osc.dto.response;

import java.time.Instant;
import java.util.Map;

public record CampaignQuoteDTO(String quoteId,long campaignId,long netAmountCents,long processingFeeCents,
        long platformFeeCents,long additionalKnownFeeCents,long totalCents,String currency,
        String paymentMethod,String feeRuleVersion,Instant expiresAt,boolean accountCostsConfirmed,
        boolean marginGuaranteed,String pricingNote,Map<String,Object> breakdown) { }
