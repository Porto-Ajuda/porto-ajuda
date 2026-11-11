package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.service.GeocodingService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/geocoding")
public class GeocodingController {

    private final GeocodingService geocodingService;

    @GetMapping("/location")
    public ResponseEntity<Coordenadas> localizar(
            @RequestParam @Size(max = 100) String cidade,
            @RequestParam(required = false) @Size(max = 80) String estado,
            @RequestParam(required = false) @Size(max = 9) String cep) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(geocodingService.buscarCoordenadasBusca(cidade, estado, cep));
    }
}
