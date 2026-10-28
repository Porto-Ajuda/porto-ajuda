package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.response.Coordenadas;
import com.portoajuda.aplicacao_osc.dto.response.NominatimResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Service
public class GeocodingService {

    private static final long INTERVALO_MINIMO_MS = 1_000;
    private static final long CACHE_TTL_MS = Duration.ofHours(24).toMillis();
    private static final int LIMITE_CACHE = 1_000;

    private final RestClient restClient;
    private final Map<String, CoordenadasCacheadas> cache = new HashMap<>();
    private long inicioUltimaConsulta;

    public GeocodingService(
            @Value("${app.geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "PortoAjuda/1.0 (+https://www.portoajuda.com.br)")
                .build();
    }

    /**
     * Geocodifica somente no envio do cadastro. A sincronização limita a uma
     * chamada simultânea e mantém pelo menos um segundo entre consultas.
     */
    public synchronized Coordenadas buscarCoordenadas(
            String pais,
            String estado,
            String cep,
            String cidade,
            String rua,
            String numero) {

        validarEndereco(pais, estado, cidade, rua);
        String chave = chaveCache(pais, estado, cep, cidade, rua, numero);
        long agora = System.currentTimeMillis();
        removerCacheExpirado(agora);

        CoordenadasCacheadas resultadoCacheado = cache.get(chave);
        if (resultadoCacheado != null) {
            return resultadoCacheado.coordenadas();
        }

        aguardarIntervalo(agora);
        inicioUltimaConsulta = System.currentTimeMillis();

        try {
            NominatimResponse[] resposta = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/search")
                                .queryParam("street", montarRua(rua, numero))
                                .queryParam("city", cidade)
                                .queryParam("state", estado)
                                .queryParam("country", pais)
                                .queryParam("countrycodes", "br")
                                .queryParam("postalcode", cep)
                                .queryParam("format", "jsonv2")
                                .queryParam("limit", 1);
                        return builder.build();
                    })
                    .retrieve()
                    .body(NominatimResponse[].class);

            if (resposta == null || resposta.length == 0) {
                throw new ResponseStatusException(
                        UNPROCESSABLE_ENTITY,
                        "O endereço informado não foi localizado. Confira o CEP, a cidade, a rua e o número.");
            }

            Coordenadas coordenadas = new Coordenadas(
                    Double.parseDouble(resposta[0].lat()),
                    Double.parseDouble(resposta[0].lon()));

            if (!coordenadasValidas(coordenadas)) {
                throw new ResponseStatusException(
                        BAD_GATEWAY,
                        "O serviço de localização retornou coordenadas inválidas.");
            }

            guardarCache(chave, coordenadas, System.currentTimeMillis());
            return coordenadas;
        } catch (ResponseStatusException erro) {
            throw erro;
        } catch (RestClientException | NumberFormatException erro) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Não foi possível localizar o endereço agora. Tente novamente mais tarde.",
                    erro);
        }
    }

    private void validarEndereco(
            String pais,
            String estado,
            String cidade,
            String rua) {
        if (pais == null || pais.isBlank()
                || estado == null || estado.isBlank()
                || cidade == null || cidade.isBlank()
                || rua == null || rua.isBlank()) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "Informe país, estado, cidade e rua para localizar o endereço.");
        }

        if (!pais.equalsIgnoreCase("Brasil") && !pais.equalsIgnoreCase("Brazil")) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "O cadastro de OSC está disponível para endereços no Brasil.");
        }
    }

    private String montarRua(String rua, String numero) {
        return numero == null || numero.isBlank()
                ? rua.trim()
                : rua.trim() + ", " + numero.trim();
    }

    private void aguardarIntervalo(long agora) {
        long restante = inicioUltimaConsulta + INTERVALO_MINIMO_MS - agora;
        if (inicioUltimaConsulta == 0 || restante <= 0) {
            return;
        }

        try {
            Thread.sleep(restante);
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "A consulta de localização foi interrompida. Tente novamente.",
                    erro);
        }
    }

    private void removerCacheExpirado(long agora) {
        cache.entrySet().removeIf(entrada -> entrada.getValue().expiraEm() <= agora);
    }

    private void guardarCache(String chave, Coordenadas coordenadas, long agora) {
        if (cache.size() >= LIMITE_CACHE) {
            cache.clear();
        }
        cache.put(chave, new CoordenadasCacheadas(coordenadas, agora + CACHE_TTL_MS));
    }

    private String chaveCache(String... componentes) {
        return java.util.Arrays.stream(componentes)
                .map(valor -> valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private boolean coordenadasValidas(Coordenadas coordenadas) {
        return Double.isFinite(coordenadas.latitude())
                && coordenadas.latitude() >= -90
                && coordenadas.latitude() <= 90
                && Double.isFinite(coordenadas.longitude())
                && coordenadas.longitude() >= -180
                && coordenadas.longitude() <= 180;
    }

    private record CoordenadasCacheadas(Coordenadas coordenadas, long expiraEm) {
    }
}
