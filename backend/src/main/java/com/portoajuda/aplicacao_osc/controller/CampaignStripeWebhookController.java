package com.portoajuda.aplicacao_osc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.service.CampaignStripeEventProcessor;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CampaignStripeWebhookController {
    private final CampaignStripeEventProcessor processor;
    private final ObjectMapper mapper;
    private final String signingSecret;
    public CampaignStripeWebhookController(CampaignStripeEventProcessor processor,
            @Value("${STRIPE_CAMPAIGN_WEBHOOK_SECRET:}") String signingSecret){
        this.processor=processor;this.mapper=new ObjectMapper();this.signingSecret=signingSecret;
    }
    @PostMapping("/stripe/campaign-webhook")
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
            @RequestHeader(value="Stripe-Signature",required=false) String signature){
        if(signingSecret.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Campaign Stripe webhook is not configured.");
        if(signature==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Missing Stripe signature.");
        try{
            Event verified=Webhook.constructEvent(rawBody,signature,signingSecret);
            JsonNode root=mapper.readTree(rawBody);
            JsonNode object=root.path("data").path("object");
            processor.process(verified.getId(),verified.getType(),verified.getAccount(),verified.getLivemode(),object);
            return ResponseEntity.ok().build();
        }catch(SignatureVerificationException error){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Stripe webhook signature or payload.");
        }catch(java.io.IOException error){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Stripe webhook payload.");
        }
    }
}
