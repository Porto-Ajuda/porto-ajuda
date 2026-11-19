package com.portoajuda.aplicacao_osc.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.portoajuda.aplicacao_osc.service.CampaignDonationPaymentService;
import com.portoajuda.aplicacao_osc.service.DonationCampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.http.HttpStatus;
import java.sql.SQLException;

class DonationCampaignControllerTest {
    private final DonationCampaignController controller = new DonationCampaignController(
            mock(DonationCampaignService.class), mock(CampaignDonationPaymentService.class));

    @Test void databaseFailuresReturnOnlyRetryablePortugueseMessage(){
        var response=controller.databaseFailure(new BadSqlGrammarException("save campaign",
                "INSERT INTO donation_campaigns(deadline) VALUES (?)", new SQLException("driver failure", "07006")));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,response.getStatusCode());
        assertEquals("Não foi possível salvar a vaquinha agora. Seus dados foram mantidos; tente novamente.",
                response.getBody().get("message"));
        assertFalse(response.getBody().toString().contains("INSERT INTO"));
        assertFalse(response.getBody().toString().contains("07006"));
    }
}
