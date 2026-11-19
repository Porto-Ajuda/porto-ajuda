package com.portoajuda.aplicacao_osc.service;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CampaignQuotePolicy {
    private CampaignQuotePolicy() { }
    public static void requireCurrent(Instant expiresAt,Instant now) {
        if(expiresAt==null||now==null||!expiresAt.isAfter(now))
            throw new ResponseStatusException(HttpStatus.GONE,"A cotação expirou. Solicite uma nova cotação.");
    }
    public static long amountForNet(long netCents) {
        if(netCents<500||netCents>50_000_000L)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"O valor líquido deve ser entre R$ 5,00 e R$ 500.000,00.");
        return netCents;
    }
    public static void requireOwner(int quoteDonorId,int authenticatedDonorId){
        if(quoteDonorId!=authenticatedDonorId)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Esta cotação pertence a outra conta.");
    }
}
