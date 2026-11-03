package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.RegisterStripe;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stripe")
public class StriveController {
    private final RegisterStripe registerStripe;

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
}