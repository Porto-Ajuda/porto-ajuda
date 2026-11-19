package com.portoajuda.aplicacao_osc.dto.response;

public record CampaignPaymentStatusDTO(String status,String campaignTitle,long ngoNetCents,long progressNetCents,
        String message,long feeShortfallCents) { }
