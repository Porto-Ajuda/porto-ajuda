package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.portoajuda.aplicacao_osc.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Read-only feature-disabled startup/API regression, gated by an exact TEST host confirmation. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "server.port=0",
        "app.cookies.secure=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.initialization.seed-roles=false",
        "app.maintenance.cleanup-cron=-",
        "app.admin-auth.controls-cleanup-cron=-",
        "app.content-reporting.enabled=false"
})
@ExtendWith(ContentReportingTestDatabaseCondition.class)
class ContentReportingDisabledSupabaseTest {
    @Autowired ContentReportService reports;
    @LocalServerPort int port;
    private final java.net.http.HttpClient http=java.net.http.HttpClient.newHttpClient();
    @MockitoBean EmailService emailService;
    @MockitoBean SmsService smsService;
    @MockitoBean AdminAuthDeliveryWorker adminDeliveryWorker;
    @MockitoBean SupabaseStorageService storageService;

    @Test
    void startsWithHibernateValidateAndPublicCatalogsWorkWhileReportingIsDisabled() throws Exception {
        assertFalse(reports.isEnabled());
        assertStatus("/osc/list?page=0&size=1");
        assertStatus("/post/catalogo/publicacoes?page=0&size=1");
        assertStatus("/post/catalogo/voluntariados?page=0&size=1");
    }

    private void assertStatus(String path) throws Exception {
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+path)).GET().build();
        var response=http.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.OK.value(), response.statusCode(), "Read-only public endpoint failed: " + path);
    }
}
