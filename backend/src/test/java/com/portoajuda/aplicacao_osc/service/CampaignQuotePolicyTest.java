package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CampaignQuotePolicyTest {
    @Test void quoteExpiresAtBoundaryAndRequiresRenewal(){
        Instant now=Instant.parse("2026-11-18T12:00:00Z");
        CampaignQuotePolicy.requireCurrent(now.plusSeconds(1),now);
        assertEquals(410,assertThrows(ResponseStatusException.class,()->CampaignQuotePolicy.requireCurrent(now,now)).getStatusCode().value());
        assertEquals(410,assertThrows(ResponseStatusException.class,()->CampaignQuotePolicy.requireCurrent(now.minusSeconds(1),now)).getStatusCode().value());
    }
}
