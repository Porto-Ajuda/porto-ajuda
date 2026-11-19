package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.controller.CampaignStripeWebhookController;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CampaignStripeWebhookControllerTest {
    private static final String SECRET="whsec_test_mock";
    private final CampaignStripeEventProcessor processor=mock(CampaignStripeEventProcessor.class);
    private final CampaignStripeWebhookController controller=new CampaignStripeWebhookController(processor,SECRET);
    private final String payload="""
        {"id":"evt_test_123","object":"event","api_version":"2025-01-27.acacia","created":1795003200,
         "data":{"object":{"object":"test_object","id":"obj_1"}},"livemode":false,"pending_webhooks":1,
         "request":{"id":null,"idempotency_key":null},"type":"unrelated.test_event","account":"acct_test_1"}
        """;

    @Test void validStripeSignatureIsPassedToProcessor(){
        String signature=signature(payload,Instant.now().getEpochSecond());
        assertEquals(200,controller.receive(payload,signature).getStatusCode().value());
        verify(processor).process(eq("evt_test_123"),eq("unrelated.test_event"),eq("acct_test_1"),eq(false),any());
    }

    @Test void invalidSignatureDoesNotReachProcessor(){
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->controller.receive(payload,"t=1,v1=invalid"));
        assertEquals(400,error.getStatusCode().value());verifyNoInteractions(processor);
    }

    private static String signature(String body,long timestamp){
        try{
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String signed=timestamp+"."+body;String digest=HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
            return "t="+timestamp+",v1="+digest;
        }catch(Exception error){throw new IllegalStateException(error);}
    }
}
