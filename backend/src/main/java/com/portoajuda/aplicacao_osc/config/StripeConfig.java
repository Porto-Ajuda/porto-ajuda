package com.portoajuda.aplicacao_osc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stripe.StripeClient;

@Configuration 
public class StripeConfig {
    @Bean
    public StripeClient stripeClient(@Value("${SECRET_KEY}") String apiKey) {
        return new StripeClient(apiKey);
    }
}