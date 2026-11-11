package com.portoajuda.aplicacao_osc.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeocodingServiceTest {

    private HttpServer server;
    private GeocodingService service;
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<String> requestedQuery = new AtomicReference<>();

    @BeforeEach
    void startMockGeocoder() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            requestedQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        service = new GeocodingService("http://127.0.0.1:" + server.getAddress().getPort());
        responseBody.set("[{\"lat\":\"-23.99\",\"lon\":\"-46.40\"}]");
    }

    @AfterEach
    void stopMockGeocoder() {
        if (server != null) server.stop(0);
    }

    @Test
    void resolveCidadeEstadoComCoordenadasDoGeocodificadorMockado() {
        var coordinates = service.buscarCoordenadasBusca("Praia Grande", "SP", null);

        assertEquals(-23.99, coordinates.latitude());
        assertEquals(-46.40, coordinates.longitude());
        assertEquals("Praia Grande", query().get("city"));
        assertEquals("SP", query().get("state"));
        assertEquals("1", query().get("limit"));
    }

    @Test
    void resolveCepComCidadeEEstadoSemPersistirDadosDeUsuario() {
        var coordinates = service.buscarCoordenadasBusca("Praia Grande", "SP", "11700-000");

        assertEquals(-23.99, coordinates.latitude());
        assertEquals("11700000", query().get("postalcode"));
        assertEquals("SP", query().get("state"));
    }

    @Test
    void solicitaEstadoQuandoCidadeSemEstadoRetornaMaisDeUmaCorrespondencia() {
        responseBody.set("[{\"lat\":\"-23.99\",\"lon\":\"-46.40\"},{\"lat\":\"-24.00\",\"lon\":\"-46.41\"}]");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.buscarCoordenadasBusca("Praia Grande", null, null));

        assertEquals(409, error.getStatusCode().value());
        assertEquals("2", query().get("limit"));
    }

    @Test
    void mapeiaRateLimitDoProvedorSemTrataLoComoCidadeInexistente() {
        responseStatus.set(429);
        responseBody.set("{}");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.buscarCoordenadasBusca("Recife", "PE", null));

        assertEquals(429, error.getStatusCode().value());
    }

    @Test
    void rejeitaCoordenadasInvalidasDoProvedor() {
        responseBody.set("[{\"lat\":\"999\",\"lon\":\"-46.40\"}]");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.buscarCoordenadasBusca("Recife", "PE", null));

        assertEquals(502, error.getStatusCode().value());
    }

    @Test
    void rejeitaEntradaInvalidaSemChamarProvedor() {
        ResponseStatusException cityError = assertThrows(ResponseStatusException.class,
                () -> service.buscarCoordenadasBusca(" ", "SP", null));
        ResponseStatusException postalError = assertThrows(ResponseStatusException.class,
                () -> service.buscarCoordenadasBusca("Recife", "PE", "abc"));
        ResponseStatusException stateError = assertThrows(ResponseStatusException.class,
                () -> service.buscarCoordenadasBusca("Recife", "XX", null));

        assertEquals(400, cityError.getStatusCode().value());
        assertEquals(400, postalError.getStatusCode().value());
        assertEquals(400, stateError.getStatusCode().value());
        assertEquals(null, requestedQuery.get());
    }

    private Map<String, String> query() {
        return Arrays.stream(requestedQuery.get().split("&"))
                .map(entry -> entry.split("=", 2))
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8)));
    }
}
