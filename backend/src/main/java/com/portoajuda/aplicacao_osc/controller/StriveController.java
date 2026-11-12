package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.CheckoutStripe;
import com.portoajuda.aplicacao_osc.service.RegisterStripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stripe")
public class StriveController {
    private final RegisterStripe registerStripe;
    private final CheckoutStripe checkoutStripe;

    @PreAuthorize("hasRole('OSC')")
    @GetMapping("/account-link")
    public ResponseEntity<Void> getAccountLink(@AuthenticationPrincipal Usuario usuario) {
        try {
             String url = registerStripe.createOnboardingLink(registerStripe.createStripeAccount(usuario));
            return ResponseEntity
                    .status(HttpStatus.SEE_OTHER)
                    .location(URI.create(url))
                    .build();
        } catch (StripeException e) {
            throw new RuntimeException("Erro ao gerar link de cadastro da conta Stripe");
        }
    }

    @PreAuthorize("hasRole('OSC')")
    @GetMapping("/refresh/account-onboarding")
    public ResponseEntity<Void> refresh(@AuthenticationPrincipal Usuario usuario) throws StripeException {

        String url = registerStripe.refreshOnboardingLink(usuario);

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