package com.portoajuda.aplicacao_osc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @GetMapping("/database")
    public Map<String, String> database() {
        return Map.of("status", "SUPABASE");
    }
}
