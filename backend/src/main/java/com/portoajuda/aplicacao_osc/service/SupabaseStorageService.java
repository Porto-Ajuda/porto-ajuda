
package com.portoajuda.aplicacao_osc.service;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.core.ParameterizedTypeReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SupabaseStorageService {
    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final RestClient restClient;
    private final RestClient deleteClient;
    private final String supabaseUrl;
    private final String secretKey;
    private final String bucket;
    private final ImageUploadProcessor imageProcessor;

    @Autowired
    public SupabaseStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            @Value("${supabase.bucket-publico:imagens-publicas}") String bucket,
            ImageUploadProcessor imageProcessor) {

        this.restClient = RestClient.builder().build();
        this.deleteClient = RestClient.builder().requestFactory(
                new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(
                        org.apache.hc.client5.http.impl.classic.HttpClients.custom().disableAutomaticRetries().build()))
                .build();
        this.supabaseUrl = supabaseUrl.replaceAll("/+$", "");
        this.secretKey = secretKey;
        this.bucket = bucket;
        this.imageProcessor = imageProcessor;
    }

    public SupabaseStorageService(String supabaseUrl, String secretKey, String bucket) {
        this(supabaseUrl, secretKey, bucket, new ImageUploadProcessor());
    }

    public String enviarFotoPerfil(
            Integer usuarioId,
            byte[] arquivo,
            String contentType) {

        if (usuarioId == null || usuarioId <= 0) {
            throw new IllegalArgumentException("Usuário inválido.");
        }

        ImageUploadProcessor.ProcessedImage processed = imageProcessor.process(arquivo, contentType);
        String tipo = processed.mediaType();
        String extensao = processed.extension();

        String caminho = "usuarios/" + usuarioId
                + "/foto-perfil-" + UUID.randomUUID()
                + "." + extensao;

        String endpoint = supabaseUrl
                + "/storage/v1/object/"
                + bucket + "/" + caminho;

        try {
            restClient.post().uri(endpoint).header("apikey", secretKey)
                    .header(HttpHeaders.CONTENT_TYPE, tipo).header("x-upsert", "false")
                    .body(processed.bytes()).retrieve().toBodilessEntity();
        } catch (RuntimeException error) {
            try { removerObjeto(caminho); } catch (RuntimeException cleanupFailure) { error.addSuppressed(cleanupFailure); }
            throw error;
        }

        return supabaseUrl
                + "/storage/v1/object/public/"
                + bucket + "/" + caminho;
    }

    public ArquivoEnviado enviarLogoOsc(
            Integer usuarioId,
            byte[] arquivo,
            String contentType) {
        if (usuarioId == null || usuarioId <= 0) throw new IllegalArgumentException("Usuário inválido.");
        ImageUploadProcessor.ProcessedImage processed = imageProcessor.process(arquivo, contentType);

        String tipo = processed.mediaType();
        String extensao = processed.extension();
        String caminho = "oscs/" + usuarioId
                + "/logo-" + UUID.randomUUID() + "." + extensao;
        String endpoint = supabaseUrl + "/storage/v1/object/" + bucket + "/" + caminho;

        try {
            restClient.post().uri(endpoint).header("apikey", secretKey)
                    .header(HttpHeaders.CONTENT_TYPE, tipo).header("x-upsert", "false")
                    .body(processed.bytes()).retrieve().toBodilessEntity();
        } catch (RuntimeException error) {
            try { removerLogoOsc(caminho); } catch (RuntimeException cleanupFailure) { error.addSuppressed(cleanupFailure); }
            throw error;
        }

        String url = supabaseUrl
                + "/storage/v1/object/public/"
                + bucket + "/" + caminho;
        return new ArquivoEnviado(url, caminho);
    }

    public void removerLogoOsc(String caminho) {
        if (caminho == null || !caminho.matches("oscs/\\d+/logo-" + UUID_PATTERN + "\\.(jpg|png|webp)")) return;
        removerObjeto(caminho);
    }

    public void removerLogoOscUrl(String url) {
        String key = managedImageKey(url);
        if (key != null && key.matches("oscs/\\d+/logo-" + UUID_PATTERN + "\\.(jpg|png|webp)")) removerObjeto(key);
    }

    public ArquivoEnviado enviarImagemConteudoOsc(
            Integer oscId,
            Integer postId,
            boolean voluntariado,
            byte[] arquivo,
            String contentType) {
        if (oscId == null || oscId <= 0 || postId == null || postId <= 0) {
            throw new IllegalArgumentException("Organização ou conteúdo inválido.");
        }
        ImageUploadProcessor.ProcessedImage processed = imageProcessor.process(arquivo, contentType);
        String tipo = processed.mediaType();
        String extensao = processed.extension();
        String categoria = voluntariado ? "voluntariado" : "posts";
        String caminho = "oscs/" + oscId + "/" + categoria + "/" + postId
                + "/" + UUID.randomUUID() + "." + extensao;
        String endpoint = supabaseUrl + "/storage/v1/object/" + bucket + "/" + caminho;

        try {
            restClient.post()
                    .uri(endpoint)
                    .header("apikey", secretKey)
                    .header(HttpHeaders.CONTENT_TYPE, tipo)
                    .header("x-upsert", "false")
                    .body(processed.bytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException erro) {
            try {
                removerImagemConteudoOsc(oscId, postId, voluntariado, caminho);
            } catch (RuntimeException falhaLimpeza) {
                erro.addSuppressed(falhaLimpeza);
            }
            throw erro;
        }

        String url = supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + caminho;
        return new ArquivoEnviado(url, caminho);
    }

    public ArquivoEnviado enviarImagemCampanhaOsc(Integer oscId, Integer usuarioId, byte[] arquivo, String contentType) {
        if (oscId == null || oscId <= 0 || usuarioId == null || usuarioId <= 0)
            throw new IllegalArgumentException("Organização ou usuário inválido.");
        ImageUploadProcessor.ProcessedImage processed = imageProcessor.process(arquivo, contentType);
        String caminho = "oscs/" + oscId + "/campanhas/" + UUID.randomUUID() + "." + processed.extension();
        String endpoint = supabaseUrl + "/storage/v1/object/" + bucket + "/" + caminho;
        try {
            restClient.post().uri(endpoint).header("apikey", secretKey)
                    .header(HttpHeaders.CONTENT_TYPE, processed.mediaType()).header("x-upsert", "false")
                    .body(processed.bytes()).retrieve().toBodilessEntity();
        } catch (RuntimeException error) {
            try { removerObjeto(caminho); } catch (RuntimeException cleanupFailure) { error.addSuppressed(cleanupFailure); }
            throw error;
        }
        return new ArquivoEnviado(supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + caminho, caminho);
    }

    public void removerImagemConteudoOsc(
            Integer oscId, Integer postId, boolean voluntariado, String caminho) {
        if (oscId == null || postId == null || caminho == null) return;
        String categoria = voluntariado ? "voluntariado" : "posts";
        String prefixo = "oscs/" + oscId + "/" + categoria + "/" + postId + "/";
        String nome = caminho.startsWith(prefixo) ? caminho.substring(prefixo.length()) : "";
        if (!nome.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            throw new IllegalArgumentException("Caminho de imagem fora do conteúdo autorizado.");
        }
        removerObjeto(caminho);
    }

    public void removerImagemConteudoOscUrl(
            Integer oscId, Integer postId, boolean voluntariado, String url) {
        if (oscId == null || postId == null) return;
        String categoria = voluntariado ? "voluntariado" : "posts";
        String key = managedImageKey(url);
        String expected = "oscs/" + oscId + "/" + categoria + "/" + postId + "/";
        if (key != null && key.startsWith(expected)
                && key.substring(expected.length()).matches(UUID_PATTERN + "\\.(jpg|png)")) removerObjeto(key);
    }

    public String managedImageKey(String url) {
        if (url == null || url.isBlank()) return null;
        String prefix = supabaseUrl + "/storage/v1/object/public/" + bucket + "/";
        if (!url.startsWith(prefix) || url.contains("?") || url.contains("#")) return null;
        String key = url.substring(prefix.length());
        return key.matches("usuarios/\\d+/foto-perfil-" + UUID_PATTERN + "\\.(jpg|png|webp)")
                || key.matches("oscs/\\d+/logo-" + UUID_PATTERN + "\\.(jpg|png|webp)")
                || key.matches("oscs/\\d+/campanhas/" + UUID_PATTERN + "\\.(jpg|png|webp)")
                || key.matches("oscs/\\d+/(posts|voluntariado)/\\d+/" + UUID_PATTERN + "\\.(jpg|png)")
                ? key : null;
    }

    public String urlPublicaDaImagemGerenciada(String key) {
        if (!isManagedImageKey(key)) return null;
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + key;
    }

    public boolean isManagedImageKey(String key) {
        if (key == null) return false;
        return key.matches("usuarios/\\d+/foto-perfil-" + UUID_PATTERN + "\\.(jpg|png|webp)")
                || key.matches("oscs/\\d+/logo-" + UUID_PATTERN + "\\.(jpg|png|webp)")
                || key.matches("oscs/\\d+/campanhas/" + UUID_PATTERN + "\\.(jpg|png|webp)")
                || key.matches("oscs/\\d+/(posts|voluntariado)/\\d+/" + UUID_PATTERN + "\\.(jpg|png)");
    }

    public void removerImagemGerenciadaKey(String key) {
        if (isManagedImageKey(key)) removerObjeto(key);
    }

    public void removerImagemGerenciadaUrl(String url) {
        String key = managedImageKey(url);
        if (key != null) removerObjeto(key);
    }

    /** Read-only inventory of the application's managed namespaces in the public image bucket. */
    public List<ManagedStoredObject> listarImagensGerenciadas() {
        List<ManagedStoredObject> objects = new ArrayList<>();
        listPrefix("usuarios/", objects);
        listPrefix("oscs/", objects);
        return List.copyOf(objects);
    }

    private void listPrefix(String prefix, List<ManagedStoredObject> objects) {
        int offset = 0;
        final int limit = 500;
        while (true) {
            List<Map<String, Object>> page = restClient.post()
                    .uri(supabaseUrl + "/storage/v1/object/list/" + bucket)
                    .header("apikey", secretKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("prefix", prefix, "limit", limit, "offset", offset,
                            "sortBy", Map.of("column", "name", "order", "asc")))
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            if (page == null) throw new IllegalStateException("O inventário do armazenamento retornou uma resposta vazia.");
            for (Map<String, Object> item : page) {
                Object rawName = item.get("name");
                if (!(rawName instanceof String name) || name.isBlank() || name.contains("..") || name.startsWith("/")) continue;
                String key = prefix + name;
                if (item.get("id") == null) {
                    listPrefix(key.endsWith("/") ? key : key + "/", objects);
                } else {
                    String created = item.get("created_at") instanceof String value ? value : null;
                    objects.add(new ManagedStoredObject(key, parseCreatedAt(created)));
                }
            }
            if (page.size() < limit) break;
            offset += limit;
        }
    }

    private Instant parseCreatedAt(String value) {
        if (value == null || value.isBlank()) return null;
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (RuntimeException ignored) { return null; }
    }

    public record ManagedStoredObject(String key, Instant createdAt) { }

    public void removerFotoPerfilUrl(Integer usuarioId, String url) {
        if (usuarioId == null || usuarioId <= 0) return;
        String key = managedImageKey(url);
        if (key != null && key.startsWith("usuarios/" + usuarioId + "/")) removerObjeto(key);
    }

    private void removerObjeto(String key) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                deleteClient.delete().uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + key)
                        .header("apikey", secretKey).retrieve().toBodilessEntity();
                return;
            } catch (HttpStatusCodeException error) {
                if (error.getStatusCode() == HttpStatus.NOT_FOUND) return;
                if (attempt == 3) throw error;
            } catch (RuntimeException error) {
                if (attempt == 3) throw error;
            }
            try { Thread.sleep(100L * attempt); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Limpeza da imagem interrompida.");
            }
        }
    }

    public record ArquivoEnviado(String url, String caminho) {
    }
}
