
package com.portoajuda.aplicacao_osc.service;

import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SupabaseStorageService {

    private static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;
    private static final long PIXELS_MAXIMOS = 12_000_000L;
    private static final int DIMENSAO_MAXIMA = 6000;

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

    public void removerLogoOscUrl(String url) {
        if (url == null || url.isBlank()) return;
        String marcador = "/" + bucket + "/";
        int indice = url.indexOf(marcador);
        if (indice < 0) return;
        String caminho = url.substring(indice + marcador.length());
        int query = caminho.indexOf('?');
        if (query >= 0) caminho = caminho.substring(0, query);
        removerLogoOsc(caminho);
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
        String tipo = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        String extensao = validarImagemConteudo(arquivo, tipo);
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
                    .body(arquivo)
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

    public void removerImagemConteudoOsc(
            Integer oscId, Integer postId, boolean voluntariado, String caminho) {
        if (oscId == null || postId == null || caminho == null) return;
        String categoria = voluntariado ? "voluntariado" : "posts";
        String prefixo = "oscs/" + oscId + "/" + categoria + "/" + postId + "/";
        String nome = caminho.startsWith(prefixo) ? caminho.substring(prefixo.length()) : "";
        if (!nome.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            throw new IllegalArgumentException("Caminho de imagem fora do conteúdo autorizado.");
        }
        restClient.delete()
                .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + caminho)
                .header("apikey", secretKey)
                .retrieve()
                .toBodilessEntity();
    }

    public void removerImagemConteudoOscUrl(
            Integer oscId, Integer postId, boolean voluntariado, String url) {
        if (url == null || url.isBlank()) return;
        String prefixoPublico = supabaseUrl + "/storage/v1/object/public/" + bucket + "/";
        if (!url.startsWith(prefixoPublico)) return;
        removerImagemConteudoOsc(oscId, postId, voluntariado,
                url.substring(prefixoPublico.length()));
    }

    private String validarImagemConteudo(byte[] arquivo, String contentType) {
        if (arquivo == null || arquivo.length == 0) {
            throw new IllegalArgumentException("Selecione uma imagem válida.");
        }
        if (arquivo.length > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("A imagem não pode ultrapassar 10 MB.");
        }
        String extensao = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new IllegalArgumentException("Formato permitido: JPEG ou PNG.");
        };
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(arquivo))) {
            if (entrada == null) throw new IllegalArgumentException("Arquivo de imagem inválido.");
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) throw new IllegalArgumentException("Arquivo de imagem inválido.");
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada, true, true);
                String formato = leitor.getFormatName().toLowerCase(java.util.Locale.ROOT);
                boolean corresponde = extensao.equals("jpg")
                        ? formato.equals("jpeg") || formato.equals("jpg")
                        : formato.equals("png");
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                if (!corresponde || largura < 1 || altura < 1
                        || largura > DIMENSAO_MAXIMA || altura > DIMENSAO_MAXIMA
                        || (long) largura * altura > PIXELS_MAXIMOS
                        || leitor.read(0) == null) {
                    throw new IllegalArgumentException("Arquivo de imagem inválido ou muito grande.");
                }
            } finally {
                leitor.dispose();
            }
        } catch (IOException | RuntimeException erro) {
            if (erro instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("Arquivo de imagem inválido.");
        }
        return extensao;
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
