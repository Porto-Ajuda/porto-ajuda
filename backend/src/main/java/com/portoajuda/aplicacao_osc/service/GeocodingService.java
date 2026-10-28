package com.portoajuda.aplicacao_osc.service;
import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.dto.response.NominatimResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeocodingService {
    private final RestClient restClient;

    public GeocodingService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "PortoAjuda/1.0"
                )
                .build();
    }

    public Coordenadas buscarCoordenadas(
            String cep,
            String cidade,
            String rua,
            String numero
    ) {
        String endereco = rua + ", " + numero + ", "
                + cidade + ", São Paulo, Brasil";

        NominatimResponse[] resposta = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", endereco)
                        .queryParam("countrycodes", "br")
                        .queryParam("format", "jsonv2")
                        .queryParam("limit", 1)
                        .build())
                .retrieve()
                .body(NominatimResponse[].class);

        if (resposta == null || resposta.length == 0) {
            throw new IllegalArgumentException(
                    "Não foi possível localizar o endereço: " + endereco
            );
        }

        return new Coordenadas(
                Double.parseDouble(resposta[0].lat()),
                Double.parseDouble(resposta[0].lon())
        );
    }
}