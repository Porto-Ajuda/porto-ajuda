package com.portoajuda.aplicacao_osc;

import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.service.GeocodingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class test implements CommandLineRunner {
    private final GeocodingService geocodingService;

    public test(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @Override
    public void run(String... args) {

        Coordenadas coordenadas = geocodingService.buscarCoordenadas(
                "11700-000",
                "Praia Grande",
                "Avenida Presidente Kennedy",
                "123"
        );

        System.out.println("Latitude: " + coordenadas.latitude());
        System.out.println("Longitude: " + coordenadas.longitude());
    }
}
