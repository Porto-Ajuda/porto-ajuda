package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.portoajuda.aplicacao_osc.config.SmsMaisProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SmsMaisClientTest {

    private static final String TOKEN_SANDBOX_FALSO = "sbx_token_de_teste_nao_utilizavel";
    private static final String CODIGO_FALSO = "741852";
    private static final String IDENTIFICADOR = "desafio-admin-teste-1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> resposta = new AtomicReference<>();
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<Long> atrasoMs = new AtomicReference<>(0L);
    private final AtomicReference<RequisicaoCapturada> requisicaoCapturada = new AtomicReference<>();
    private final AtomicInteger quantidadeRequisicoes = new AtomicInteger();
    private CountDownLatch requisicaoRecebida;

    private HttpServer server;
    private ExecutorService executor;
    private SmsMaisClient client;

    @BeforeEach
    void iniciarServidorHttpFalso() throws IOException {
        resposta.set("""
                [{"id":98765,"to":"5511912345678","msg":"Código de teste","externalId":"%s"}]
                """.formatted(IDENTIFICADOR));
        status.set(200);
        atrasoMs.set(0L);
        requisicaoCapturada.set(null);
        quantidadeRequisicoes.set(0);
        requisicaoRecebida = new CountDownLatch(1);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/send", exchange -> {
            quantidadeRequisicoes.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            requisicaoCapturada.set(new RequisicaoCapturada(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body));
            requisicaoRecebida.countDown();

            long atraso = atrasoMs.get();
            if (atraso > 0) {
                try {
                    Thread.sleep(atraso);
                } catch (InterruptedException erro) {
                    Thread.currentThread().interrupt();
                }
            }

            byte[] bytes = resposta.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException desconexaoCliente) {
                // O teste de timeout fecha a conexão antes da resposta do servidor falso.
            } finally {
                exchange.close();
            }
        });
        server.start();
        client = criarClient("sandbox", TOKEN_SANDBOX_FALSO, Duration.ofSeconds(30));
    }

    @AfterEach
    void encerrarServidorHttpFalso() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void enviaPayloadOficialParaServidorLocalComTokenSandbox() throws Exception {
        SmsMaisSendResult result = client.enviar("5511912345678", "Código " + CODIGO_FALSO, IDENTIFICADOR);

        assertEquals("98765", result.providerMessageId());
        assertEquals(IDENTIFICADOR, result.externalId());
        RequisicaoCapturada request = requisicaoCapturada.get();
        assertEquals("POST", request.method());
        assertEquals("/send", request.path());
        assertEquals("Bearer " + TOKEN_SANDBOX_FALSO, request.authorization());
        assertTrue(request.contentType().startsWith("application/json"));

        JsonNode payload = objectMapper.readTree(request.body());
        JsonNode message = payload.path("messages").get(0);
        assertEquals(IDENTIFICADOR, message.path("id").asText());
        assertEquals("5511912345678", message.path("destinations").get(0).path("to").asText());
        assertEquals("Código " + CODIGO_FALSO, message.path("msg").asText());
        assertFalse(request.body().contains(TOKEN_SANDBOX_FALSO));
    }

    @Test
    void respostaHttpDeErroNaoExpoeTokenNemTextoDaMensagem() {
        status.set(401);
        resposta.set("resposta contendo " + CODIGO_FALSO + " e credenciais não deve ser propagada");

        SmsDeliveryException error = assertThrows(
                SmsDeliveryException.class,
                () -> client.enviar("5511912345678", "Código " + CODIGO_FALSO, IDENTIFICADOR));

        assertEquals(SmsDeliveryException.Reason.PROVIDER_REJECTED, error.getReason());
        assertEquals(401, error.getProviderStatus());
        assertFalse(error.getMessage().contains(TOKEN_SANDBOX_FALSO));
        assertFalse(error.getMessage().contains(CODIGO_FALSO));
        assertEquals(1, quantidadeRequisicoes.get());
    }

    @Test
    void respostaSemMensagemAceitaEConsideradaInvalida() {
        resposta.set("[]");

        SmsDeliveryException error = assertThrows(
                SmsDeliveryException.class,
                () -> client.enviar("5511912345678", "Código " + CODIGO_FALSO, IDENTIFICADOR));

        assertEquals(SmsDeliveryException.Reason.INVALID_RESPONSE, error.getReason());
    }

    @Test
    void timeoutEReportadoComoResultadoDesconhecidoSemRetryAutomatico() throws Exception {
        atrasoMs.set(2500L);
        client = criarClient("sandbox", TOKEN_SANDBOX_FALSO, Duration.ofMillis(1000));
        ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<SmsDeliveryException> response = requestExecutor.submit(() -> {
                try {
                    client.enviar("5511912345678", "test code", IDENTIFICADOR);
                    return null;
                } catch (SmsDeliveryException error) {
                    return error;
                }
            });

            assertTrue(requisicaoRecebida.await(2, TimeUnit.SECONDS),
                    "The local test server must receive the only request before its delayed response.");
            SmsDeliveryException error = response.get(3, TimeUnit.SECONDS);
            assertNotNull(error);
            assertEquals(SmsDeliveryException.Reason.TIMEOUT, error.getReason());
            assertEquals(1, quantidadeRequisicoes.get());
        } finally {
            requestExecutor.shutdownNow();
        }
    }
    @Test
    void modoSandboxRejeitaTokenQueNaoTemPrefixoDeHomologacaoSemFazerRequisicao() {
        client = criarClient("sandbox", "token_sem_prefixo_de_sandbox", Duration.ofSeconds(5));

        SmsDeliveryException error = assertThrows(
                SmsDeliveryException.class,
                () -> client.enviar("5511912345678", "Código " + CODIGO_FALSO, IDENTIFICADOR));

        assertEquals(SmsDeliveryException.Reason.CONFIGURATION, error.getReason());
        assertEquals(0, quantidadeRequisicoes.get());
    }

    @Test
    void modoProducaoRejeitaTokenDeSandboxSemFazerRequisicao() {
        client = criarClient("production", TOKEN_SANDBOX_FALSO, Duration.ofSeconds(5));

        SmsDeliveryException error = assertThrows(
                SmsDeliveryException.class,
                () -> client.enviar("5511912345678", "Código " + CODIGO_FALSO, IDENTIFICADOR));

        assertEquals(SmsDeliveryException.Reason.CONFIGURATION, error.getReason());
        assertEquals(0, quantidadeRequisicoes.get());
    }

    @Test
    void modoProducaoAceitaTokenDeProducaoSemPrefixoSandboxUsandoSomenteServidorLocal() {
        client = criarClient("production", "token_falso_de_producao", Duration.ofSeconds(5));

        SmsMaisSendResult result = client.enviar("5511912345678", "Mensagem falsa", IDENTIFICADOR);

        assertEquals("98765", result.providerMessageId());
        assertEquals("Bearer token_falso_de_producao", requisicaoCapturada.get().authorization());
        assertEquals(1, quantidadeRequisicoes.get());
    }

    @Test
    void jsonDeRespostaInvalidoNaoVazaConteudoNemToken() {
        resposta.set("resposta inválida contendo " + CODIGO_FALSO);

        SmsDeliveryException error = assertThrows(
                SmsDeliveryException.class,
                () -> client.enviar("5511912345678", "Código " + CODIGO_FALSO, IDENTIFICADOR));

        assertEquals(SmsDeliveryException.Reason.INVALID_RESPONSE, error.getReason());
        assertFalse(error.getMessage().contains(TOKEN_SANDBOX_FALSO));
        assertFalse(error.getMessage().contains(CODIGO_FALSO));
    }

    @Test
    void faltaDeTokenNaoFazRequisicao() {
        client = criarClient("sandbox", "  ", Duration.ofSeconds(1));

        SmsDeliveryException error = assertThrows(
                SmsDeliveryException.class,
                () -> client.enviar("5511912345678", "Código " + CODIGO_FALSO, IDENTIFICADOR));

        assertEquals(SmsDeliveryException.Reason.CONFIGURATION, error.getReason());
        assertEquals(0, quantidadeRequisicoes.get());
    }

    private SmsMaisClient criarClient(String environment, String token, Duration timeout) {
        SmsMaisProperties properties = new SmsMaisProperties();
        properties.setEnvironment(environment);
        properties.setToken(token);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .requestFactory(requestFactory)
                .build();
        return new SmsMaisClient(restClient, properties);
    }

    private record RequisicaoCapturada(
            String method,
            String path,
            String authorization,
            String contentType,
            String body) {
    }
}
