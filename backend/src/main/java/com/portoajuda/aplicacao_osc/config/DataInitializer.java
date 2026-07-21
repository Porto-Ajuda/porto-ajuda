package com.portoajuda.aplicacao_osc.config;

import com.portoajuda.aplicacao_osc.service.InitializeService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final InitializeService initializeService;

    @Override
    public void run(String... args) throws Exception {
        initializeService.initializeRoles();
        initializeService.initializePermissoes();
    }
}
