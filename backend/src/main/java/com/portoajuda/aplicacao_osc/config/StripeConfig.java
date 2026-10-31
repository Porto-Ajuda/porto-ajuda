package com.portoajuda.aplicacao_osc.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class StripeConfig {
    @Bean
    public StripeClient stripeClient(@Value("${SECRET_KEY}") String apiKey) {
        return new StripeClient(apiKey);
    }
}