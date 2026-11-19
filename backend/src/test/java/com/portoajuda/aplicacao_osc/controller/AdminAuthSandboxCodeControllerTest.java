package com.portoajuda.aplicacao_osc.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.service.AdminAuthenticationService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AdminAuthSandboxCodeControllerTest {
    @Mock private AdminAuthenticationService authenticationService;
    private UUID challengeId;

    @BeforeEach
    void setUp() {
        challengeId = UUID.randomUUID();
        lenient().when(authenticationService.obterCodigoSandbox(challengeId)).thenReturn("123456");
    }

    @Test
    void allowsDirectIpv4LoopbackTestRequest() {
        var response = controller("sandbox").getCode(challengeId, localRequest("127.0.0.1", "127.0.0.1:8080"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authenticationService).obterCodigoSandbox(challengeId);
    }

    @Test
    void allowsDirectIpv6LoopbackTestRequest() {
        var response = controller("sandbox").getCode(challengeId, localRequest("0:0:0:0:0:0:0:1", "[::1]:8080"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authenticationService).obterCodigoSandbox(challengeId);
    }

    @Test
    void rejectsForwardedOrNonLocalRequestsAndProductionMode() {
        MockHttpServletRequest forwarded = localRequest("127.0.0.1", "localhost:8080");
        forwarded.addHeader("X-Forwarded-For", "198.51.100.7");
        assertEquals(HttpStatus.NOT_FOUND, controller("sandbox").getCode(challengeId, forwarded).getStatusCode());

        assertEquals(HttpStatus.NOT_FOUND,
                controller("sandbox").getCode(challengeId, localRequest("192.0.2.7", "localhost:8080")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                controller("production").getCode(challengeId, localRequest("127.0.0.1", "127.0.0.1:8080")).getStatusCode());
        verify(authenticationService, never()).obterCodigoSandbox(challengeId);
    }

    @Test
    void responseDisablesCaching() {
        var response = controller("sandbox").getCode(challengeId, localRequest("::1", "[::1]:8080"));
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
    }

    private AdminAuthSandboxCodeController controller(String environment) {
        return new AdminAuthSandboxCodeController(authenticationService, environment);
    }

    private MockHttpServletRequest localRequest(String remoteAddress, String host) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("Host", host);
        return request;
    }
}
