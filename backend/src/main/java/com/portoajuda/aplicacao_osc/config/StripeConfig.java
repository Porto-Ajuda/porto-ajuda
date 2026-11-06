
package com.portoajuda.aplicacao_osc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stripe.StripeClient;

@Configuration
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(
            @Value("${STRIPE_SECRET_KEY}") String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "A variável STRIPE_SECRET_KEY não foi configurada.");
        }

        return new StripeClient(apiKey);
    }
}
