package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

class SupabaseStorageServiceContentTest {

    private HttpServer server;
    private AtomicInteger postCount;
    private AtomicInteger deleteCount;
    private AtomicReference<String> requestedPath;
    private AtomicReference<String> apiKey;
    private AtomicInteger listCount;
    private int responseOnUpload = 200;
    private int deleteFailuresRemaining;
    private SupabaseStorageService service;

    @BeforeEach
    void startLocalStorageMock() throws IOException {
        postCount = new AtomicInteger();
        deleteCount = new AtomicInteger();
        requestedPath = new AtomicReference<>();
        apiKey = new AtomicReference<>();
        listCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
            if (exchange.getRequestURI().getPath().endsWith("/list/test-images")) {
                listCount.incrementAndGet();
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String response = request.contains("usuarios/18/")
                        ? "[{\"name\":\"foto-perfil-00000000-0000-0000-0000-000000000018.png\",\"id\":\"object-id\",\"created_at\":\"2026-11-15T10:00:00Z\"}]"
                        : request.contains("usuarios/")
                                ? "[{\"name\":\"18\",\"id\":null}]" : "[]";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) postCount.incrementAndGet();
            if ("DELETE".equals(exchange.getRequestMethod())) deleteCount.incrementAndGet();
            int status = "POST".equals(exchange.getRequestMethod()) ? responseOnUpload
                    : deleteFailuresRemaining-- > 0 ? 503 : 204;
            byte[] response = "local mock".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, status == 204 ? -1 : response.length);
            if (status != 204) exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        service = new SupabaseStorageService(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-only-local-key", "test-images");
    }

    @AfterEach
    void stopLocalStorageMock() {
        if (server != null) server.stop(0);
    }

    @Test
    void contentUploadUsesServerConstructedCollisionResistantNamespace() throws Exception {
        SupabaseStorageService.ArquivoEnviado uploaded = service.enviarImagemConteudoOsc(
                12, 34, true, pngValido(), "image/png");

        assertTrue(uploaded.caminho().matches(
                "oscs/12/voluntariado/34/[0-9a-fA-F-]{36}\\.png"));
        assertTrue(uploaded.url().endsWith("/storage/v1/object/public/test-images/" + uploaded.caminho()));
        assertEquals("/storage/v1/object/test-images/" + uploaded.caminho(), requestedPath.get());
        assertEquals("test-only-local-key", apiKey.get());
        assertEquals(1, postCount.get());
        assertEquals(0, deleteCount.get());
    }

    @Test
    void emptyForgedAndMimeMismatchedImagesAreRejectedBeforeStorage() {
        assertThrows(IllegalArgumentException.class,
                () -> service.enviarImagemConteudoOsc(12, 34, false, new byte[0], "image/png"));
        assertThrows(IllegalArgumentException.class,
                () -> service.enviarImagemConteudoOsc(12, 34, false,
                        "<svg onload=alert(1) />".getBytes(StandardCharsets.UTF_8), "image/png"));
        assertThrows(IllegalArgumentException.class,
                () -> service.enviarImagemConteudoOsc(12, 34, false, pngValido(), "image/jpeg"));

        assertEquals(0, postCount.get());
    }

    @Test
    void deletionRejectsCrossOrganizationAndTraversalPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> service.removerImagemConteudoOsc(12, 34, false,
                        "oscs/99/posts/34/00000000-0000-0000-0000-000000000001.png"));
        assertThrows(IllegalArgumentException.class,
                () -> service.removerImagemConteudoOsc(12, 34, false,
                        "oscs/12/posts/34/../34/00000000-0000-0000-0000-000000000001.png"));
        assertEquals(0, deleteCount.get());
    }

    @Test
    void managedUrlParserRejectsExternalAndNonApplicationObjects() {
        assertNull(service.managedImageKey("https://cdn.example/shared.png"));
        assertNull(service.managedImageKey("http://127.0.0.1/metadata"));
        assertNull(service.managedImageKey("http://127.0.0.1:" + server.getAddress().getPort()
                + "/storage/v1/object/public/test-images/applications/private.pdf"));
        assertNull(service.managedImageKey("http://127.0.0.1:" + server.getAddress().getPort()
                + "/storage/v1/object/public/test-images/oscs/12/posts/34/../secret.png"));
        assertNotNull(service.managedImageKey("http://127.0.0.1:" + server.getAddress().getPort()
                + "/storage/v1/object/public/test-images/oscs/12/posts/34/00000000-0000-0000-0000-000000000001.png"));
    }

    @Test
    void managedDeleteRetriesTransientProviderFailures() {
        deleteFailuresRemaining = 2;
        service.removerImagemConteudoOsc(12, 34, false,
                "oscs/12/posts/34/00000000-0000-0000-0000-000000000001.png");
        assertEquals(3, deleteCount.get());
    }

    @Test
    void failedUploadAttemptsCompensatingDeleteOfTheNewObject() throws Exception {
        responseOnUpload = 500;

        assertThrows(RestClientException.class,
                () -> service.enviarImagemConteudoOsc(12, 34, false, pngValido(), "image/png"));

        assertEquals(1, postCount.get());
        assertEquals(1, deleteCount.get());
        assertTrue(requestedPath.get().startsWith("/storage/v1/object/test-images/oscs/12/posts/34/"));
    }

    @Test
    void failedProfileAndLogoUploadsAttemptCompensatingStorageCleanup() throws Exception {
        responseOnUpload = 500;
        assertThrows(RestClientException.class, () -> service.enviarFotoPerfil(18, pngValido(), "image/png"));
        assertEquals(1, deleteCount.get());

        deleteCount.set(0);
        assertThrows(RestClientException.class, () -> service.enviarLogoOsc(18, pngValido(), "image/png"));
        assertEquals(1, deleteCount.get());
    }

    @Test
    void managedInventoryUsesReadOnlyStorageListingAndKeepsOnlyNamesAndDates() {
        var objects = service.listarImagensGerenciadas();
        assertEquals(1, objects.size());
        assertEquals("usuarios/18/foto-perfil-00000000-0000-0000-0000-000000000018.png", objects.getFirst().key());
        assertNotNull(objects.getFirst().createdAt());
        assertEquals(3, listCount.get());
        assertEquals("/storage/v1/object/list/test-images", requestedPath.get());
    }

    private byte[] pngValido() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
