package com.portoajuda.aplicacao_osc.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portoajuda.aplicacao_osc.service.DoacaoService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

@RestController
public class DoacaoStripeWebhookController {
    private final DoacaoService doacoes;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String signingSecret;

    public DoacaoStripeWebhookController(DoacaoService doacoes,
            @Value("${STRIPE_DONATION_WEBHOOK_SECRET:}") String signingSecret) {
        this.doacoes = doacoes;
        this.signingSecret = signingSecret;
    }

    @PostMapping("/stripe/donation-webhook")
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Donation Stripe webhook is not configured.");
        }
        if (signature == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Stripe signature.");
        try {
            Event event = Webhook.constructEvent(rawBody, signature, signingSecret);
            JsonNode root = mapper.readTree(rawBody);
            doacoes.processarEvento(event.getId(), event.getType(), event.getAccount(), event.getLivemode(),
                    root.path("data").path("object"));
            return ResponseEntity.ok().build();
        } catch (SignatureVerificationException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook signature or payload.");
        } catch (java.io.IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook payload.");
        }
    }
}