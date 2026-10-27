
package com.portoajuda.aplicacao_osc.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SupabaseStorageService {

    private static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String secretKey;
    private final String bucket;

    public SupabaseStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            @Value("${supabase.bucket-publico:imagens-publicas}") String bucket) {

        this.restClient = RestClient.builder().build();
        this.supabaseUrl = supabaseUrl.replaceAll("/+$", "");
        this.secretKey = secretKey;
        this.bucket = bucket;
    }

    public String enviarFotoPerfil(
            Integer usuarioId,
            byte[] arquivo,
            String contentType) {

        if (usuarioId == null || usuarioId <= 0) {
            throw new IllegalArgumentException("Usuário inválido.");
        }

        if (arquivo == null || arquivo.length == 0) {
            throw new IllegalArgumentException(
                    "O arquivo enviado está vazio.");
        }

        if (arquivo.length > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "A imagem não pode ultrapassar 10 MB.");
        }

        String tipo = contentType == null
                ? ""
                : contentType.toLowerCase(java.util.Locale.ROOT);

        String extensao = switch (tipo) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException(
                    "Formato de imagem não permitido.");
        };

        String caminho = "usuarios/" + usuarioId
                + "/foto-perfil-" + UUID.randomUUID()
                + "." + extensao;

        String endpoint = supabaseUrl
                + "/storage/v1/object/"
                + bucket + "/" + caminho;

        restClient.post()
                .uri(endpoint)
                .header("apikey", secretKey)
                .header(HttpHeaders.CONTENT_TYPE, tipo)
                .header("x-upsert", "false")
                .body(arquivo)
                .retrieve()
                .toBodilessEntity();

        return supabaseUrl
                + "/storage/v1/object/public/"
                + bucket + "/" + caminho;
    }

    public ArquivoEnviado enviarLogoOsc(
            Integer usuarioId,
            byte[] arquivo,
            String contentType) {
        validarImagem(usuarioId, arquivo, contentType);

        String tipo = contentType.toLowerCase(java.util.Locale.ROOT);
        String extensao = extensaoImagem(tipo);
        String caminho = "oscs/" + usuarioId
                + "/logo-" + UUID.randomUUID() + "." + extensao;
        String endpoint = supabaseUrl + "/storage/v1/object/" + bucket + "/" + caminho;

        restClient.post()
                .uri(endpoint)
                .header("apikey", secretKey)
                .header(HttpHeaders.CONTENT_TYPE, tipo)
                .header("x-upsert", "false")
                .body(arquivo)
                .retrieve()
                .toBodilessEntity();

        String url = supabaseUrl
                + "/storage/v1/object/public/"
                + bucket + "/" + caminho;
        return new ArquivoEnviado(url, caminho);
    }

    public void removerLogoOsc(String caminho) {
        if (caminho == null || !caminho.startsWith("oscs/")) {
            return;
        }

        restClient.delete()
                .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + caminho)
                .header("apikey", secretKey)
                .retrieve()
                .toBodilessEntity();
    }

    private void validarImagem(Integer usuarioId, byte[] arquivo, String contentType) {
        if (usuarioId == null || usuarioId <= 0) {
            throw new IllegalArgumentException("Usuário inválido.");
        }
        if (arquivo == null || arquivo.length == 0) {
            throw new IllegalArgumentException("O arquivo enviado está vazio.");
        }
        if (arquivo.length > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("A imagem não pode ultrapassar 10 MB.");
        }
        extensaoImagem(contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT));
    }

    private String extensaoImagem(String tipo) {
        return switch (tipo) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Formato de imagem não permitido.");
        };
    }

    public record ArquivoEnviado(String url, String caminho) {
    }
}
