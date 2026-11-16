package com.portoajuda.aplicacao_osc.service;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Stores legal documents in a private bucket, separate from the public image bucket. */
@Service
public class SupabasePrivateVerificationEvidenceStorage implements PrivateVerificationEvidenceStorage {
    private static final int SIGNED_URL_TTL_SECONDS = 300;
    private final RestClient client = RestClient.builder().build();
    private final RestClient boundedReadClient;
    private final String supabaseUrl;
    private final String secretKey;
    private final String bucket;

    public SupabasePrivateVerificationEvidenceStorage(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            @Value("${supabase.bucket-verificacao:ngo-representative-evidence}") String bucket) {
        this.supabaseUrl = supabaseUrl.replaceAll("/+$", "");
        this.secretKey = secretKey;
        this.bucket = bucket;
        SimpleClientHttpRequestFactory readRequestFactory = new SimpleClientHttpRequestFactory();
        readRequestFactory.setConnectTimeout(Duration.ofSeconds(3));
        readRequestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.boundedReadClient = RestClient.builder().requestFactory(readRequestFactory).build();
    }

    @Override
    public String store(int oscId, long applicationId, int version, String extension, String mediaType, byte[] bytes) {
        String key = "applications/" + oscId + "/" + applicationId + "/v" + version + "/" + UUID.randomUUID() + "." + extension;
        try {
            client.post().uri(objectEndpoint(key))
                    .header("apikey", secretKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .header("x-upsert", "false")
                    .contentType(MediaType.parseMediaType(mediaType))
                    .body(bytes)
                    .retrieve().toBodilessEntity();
            return key;
        } catch (RestClientException failure) {
            throw new IllegalStateException("Não foi possível armazenar o documento privado.");
        }
    }

    @Override
    public byte[] readPrivate(String objectKey, long maximumBytes) {
        if (!isApplicationKey(objectKey) || maximumBytes < 1 || maximumBytes > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("Referência de evidência privada inválida.");
        }
        try {
            return boundedReadClient.get().uri(objectEndpoint(objectKey))
                    .header("apikey", secretKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("Não foi possível ler o documento privado.");
                        }
                        long declaredLength = response.getHeaders().getContentLength();
                        if (declaredLength > maximumBytes) {
                            throw new IllegalStateException("O documento privado excede o limite de análise.");
                        }
                        try (InputStream body = response.getBody()) {
                            byte[] bytes = body.readNBytes((int) maximumBytes + 1);
                            if (bytes.length == 0 || bytes.length > maximumBytes) {
                                throw new IllegalStateException("O conteúdo privado excede o limite de análise.");
                            }
                            return bytes;
                        } catch (IOException failure) {
                            throw new IllegalStateException("Não foi possível ler o documento privado.");
                        }
                    });
        } catch (RestClientException failure) {
            throw new IllegalStateException("Não foi possível ler o documento privado.");
        }
    }

    @Override
    public SignedAccess createShortLivedAccess(String objectKey) {
        try {
            SignedUrl response = client.post()
                    .uri(supabaseUrl + "/storage/v1/object/sign/" + bucket + "/" + objectKey)
                    .header("apikey", secretKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignedUrlRequest(SIGNED_URL_TTL_SECONDS))
                    .retrieve().body(SignedUrl.class);
            if (response == null || response.signedURL() == null || response.signedURL().isBlank()) {
                throw new IllegalStateException("Acesso temporário ao documento indisponível.");
            }
            String url = resolveSignedUrl(response.signedURL());
            return new SignedAccess(url, Instant.now().plus(SIGNED_URL_TTL_SECONDS, ChronoUnit.SECONDS));
        } catch (RestClientException failure) {
            throw new IllegalStateException("Não foi possível gerar o acesso temporário ao documento.");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.delete().uri(objectEndpoint(objectKey))
                    .header("apikey", secretKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound alreadyRemoved) {
            // An idempotent retry may follow a successful Storage delete and failed DB finalization.
        } catch (RestClientException failure) {
            throw new IllegalStateException("Não foi possível remover o arquivo privado temporário.");
        }
    }

    private String objectEndpoint(String key) {
        return supabaseUrl + "/storage/v1/object/" + bucket + "/" + key;
    }

    private boolean isApplicationKey(String key) {
        return key != null && key.startsWith("applications/") && !key.contains("..")
                && !key.contains("?") && !key.contains("#") && !key.contains("\\");
    }

    private String resolveSignedUrl(String signedUrl) {
        if (signedUrl.startsWith("http://") || signedUrl.startsWith("https://")) {
            return signedUrl;
        }
        String path = signedUrl.startsWith("/") ? signedUrl : "/" + signedUrl;
        if (!path.startsWith("/storage/v1/")) {
            path = "/storage/v1" + path;
        }
        return supabaseUrl + path;
    }

    private record SignedUrlRequest(int expiresIn) { }
    private record SignedUrl(String signedURL) { }
}
