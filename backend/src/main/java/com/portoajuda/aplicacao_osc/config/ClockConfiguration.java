package com.portoajuda.aplicacao_osc.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock applicationClock() { return Clock.systemUTC(); }
}
