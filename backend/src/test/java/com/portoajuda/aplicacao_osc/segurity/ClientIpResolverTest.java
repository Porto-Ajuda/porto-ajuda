package com.portoajuda.aplicacao_osc.segurity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void naoConfiaEmHeadersDeForwardingQuandoTrustDaEdgeEstaDesligado() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        MockHttpServletRequest request = requestFrom("10.0.0.8");
        request.addHeader("X-Real-IP", "198.51.100.90");
        request.addHeader("X-Forwarded-For", "198.51.100.91");
        request.addHeader("Forwarded", "for=198.51.100.92");

        assertEquals("10.0.0.8", resolver.resolve(request));
    }

    @Test
    void usaXRealIpValidoSomenteComContratoDeEdgeHabilitado() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest request = requestFrom("10.0.0.8");
        request.addHeader("X-Real-IP", "198.51.100.90");
        request.addHeader("X-Forwarded-For", "203.0.113.222");

        assertEquals("198.51.100.90", resolver.resolve(request));
    }

    @Test
    void headerSpoofadoXForwardedForNaoAlteraFallbackMesmoComTrustDaEdge() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest request = requestFrom("10.0.0.8");
        request.addHeader("X-Forwarded-For", "198.51.100.90");
        request.addHeader("Forwarded", "for=198.51.100.91");

        assertEquals("10.0.0.8", resolver.resolve(request));
    }

    @Test
    void valorXRealIpInvalidoOuDuplicadoCaiParaPeerSemLerPrimeiroValor() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest comma = requestFrom("10.0.0.8");
        comma.addHeader("X-Real-IP", "198.51.100.90, 203.0.113.8");
        assertEquals("10.0.0.8", resolver.resolve(comma));

        MockHttpServletRequest duplicate = requestFrom("10.0.0.9");
        duplicate.addHeader("X-Real-IP", "198.51.100.90");
        duplicate.addHeader("X-Real-IP", "203.0.113.8");
        assertEquals("10.0.0.9", resolver.resolve(duplicate));
    }

    @Test
    void aceitaIPv6LiteralESemEnderecoUsaUnknown() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest ipv6 = requestFrom("127.0.0.1");
        ipv6.addHeader("X-Real-IP", "2001:db8::8");
        assertEquals("2001:db8:0:0:0:0:0:8", resolver.resolve(ipv6));

        MockHttpServletRequest absent = new MockHttpServletRequest();
        absent.setRemoteAddr(null);
        assertEquals("unknown", resolver.resolve(absent));
    }

    private MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
