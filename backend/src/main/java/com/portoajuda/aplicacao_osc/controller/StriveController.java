package com.portoajuda.aplicacao_osc.controller;

import java.math.BigDecimal;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.CheckoutStripe;
import com.portoajuda.aplicacao_osc.service.RegisterStripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stripe")
public class StriveController {
    private final RegisterStripe registerStripe;
    private final CheckoutStripe checkoutStripe;

    @PreAuthorize("hasRole('OSC')")
    @GetMapping("/account-link")
    public ResponseEntity<Void> getAccountLink(@AuthenticationPrincipal Usuario usuario, @RequestParam Integer oscId) {
        try {
                  String url = registerStripe.createOnboardingLink(
                      registerStripe.createStripeAccount(usuario, oscId), oscId);
            return ResponseEntity
                    .status(HttpStatus.SEE_OTHER)
                    .location(URI.create(url))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar link de cadastro da conta Stripe");
        }
    }

    @PreAuthorize("hasRole('OSC')")
    @GetMapping("/refresh/account-onboarding")
    public ResponseEntity<Void> refresh(@AuthenticationPrincipal Usuario usuario, @RequestParam Integer oscId) throws StripeException {

        String url = registerStripe.refreshOnboardingLink(usuario, oscId);

        return ResponseEntity
                .status(HttpStatus.SEE_OTHER)
                .location(URI.create(url))
                .build();
    }

    @PreAuthorize("hasRole('USUARIO')")
    @PostMapping("/create-checkout-session")
    public ResponseEntity<String> createCheckoutSession(@AuthenticationPrincipal Usuario usuario, @RequestParam Integer oscId, @RequestParam String valor) throws Exception {
        Session checkoutSession = checkoutStripe.createCheckoutSession(usuario, oscId, new BigDecimal(valor));
        return ResponseEntity
                .status(HttpStatus.SEE_OTHER)
                .location(URI.create(checkoutSession.getUrl()))
                .build();
    }
}