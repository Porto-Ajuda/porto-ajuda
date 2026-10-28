package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.config.DatabaseConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final DatabaseConfig databaseConfig;

    @GetMapping("/database")
    public Map<String, String> database() {

        return Map.of(
                "status",
                databaseConfig.getStatus().name()
        );
    }
}