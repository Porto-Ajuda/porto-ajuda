package com.portoajuda.aplicacao_osc.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Private object storage. Only short-lived signed URLs leave this service after authorization. */
@Service
public class SupabaseVolunteerCertificateStorage implements VolunteerCertificateStorage {
    private static final int SIGNED_TTL_SECONDS = 300;
    private final RestClient client = RestClient.builder().build();
    private final String baseUrl;
    private final String secretKey;
    private final String bucket;

    public SupabaseVolunteerCertificateStorage(@Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            @Value("${supabase.bucket-verificacao:ngo-representative-evidence}") String bucket) {
        this.baseUrl = supabaseUrl.replaceAll("/+$", ""); this.secretKey = secretKey; this.bucket = bucket;
    }

    @Override
    public String store(Integer oscId, Integer applicationId, String extension, String contentType, byte[] content) {
        String key = "certificates/" + oscId + "/" + applicationId + "/" + UUID.randomUUID() + "." + extension;
        try {
            client.post().uri(objectUrl(key)).header("apikey", secretKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .header("x-upsert", "false").contentType(MediaType.parseMediaType(contentType))
                    .body(content).retrieve().toBodilessEntity();
            return key;
        } catch (RestClientException failure) {
            throw new IllegalStateException("Não foi possível armazenar o certificado privado.");
        }
    }

    @Override
    public SignedAccess createShortLivedAccess(String objectKey) {
        if (!isCertificateKey(objectKey)) throw new IllegalArgumentException("Referência de certificado inválida.");
        try {
            SignedUrl result = client.post().uri(baseUrl + "/storage/v1/object/sign/" + bucket + "/" + objectKey)
                    .header("apikey", secretKey).header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON).body(new SignedUrlRequest(SIGNED_TTL_SECONDS))
                    .retrieve().body(SignedUrl.class);
            if (result == null || result.signedURL() == null || result.signedURL().isBlank())
                throw new IllegalStateException("Acesso temporário ao certificado indisponível.");
            String path = result.signedURL().startsWith("/") ? result.signedURL() : "/" + result.signedURL();
            if (!path.startsWith("/storage/v1/")) path = "/storage/v1" + path;
            String url = result.signedURL().startsWith("http") ? result.signedURL() : baseUrl + path;
            return new SignedAccess(url, Instant.now().plusSeconds(SIGNED_TTL_SECONDS));
        } catch (RestClientException failure) {
            throw new IllegalStateException("Não foi possível gerar acesso temporário ao certificado.");
        }
    }

    private String objectUrl(String key) { return baseUrl + "/storage/v1/object/" + bucket + "/" + key; }
    private boolean isCertificateKey(String key) {
        return key != null && key.startsWith("certificates/") && !key.contains("..")
                && !key.contains("?") && !key.contains("#") && !key.contains("\\");
    }
    private record SignedUrlRequest(int expiresIn) {}
    private record SignedUrl(String signedURL) {}
}
