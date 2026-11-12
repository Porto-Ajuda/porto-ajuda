package com.portoajuda.aplicacao_osc.segurity;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Enumeration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads Railway's documented X-Real-IP only when the operator explicitly enables
 * the public-edge trust contract. X-Forwarded-For and Forwarded are never trusted.
 */
@Component
public class ClientIpResolver {

    private final boolean trustRailwayPublicEdgeHeader;

    public ClientIpResolver(
            @Value("${app.security.railway-public-edge-enabled:${RAILWAY_PUBLIC_EDGE_IP_HEADER_ENABLED:false}}")
                    boolean trustRailwayPublicEdgeHeader) {
        this.trustRailwayPublicEdgeHeader = trustRailwayPublicEdgeHeader;
    }

    public String resolve(HttpServletRequest request) {
        if (trustRailwayPublicEdgeHeader) {
            String forwarded = singleValidIpHeader(request, "X-Real-IP");
            if (forwarded != null) {
                return forwarded;
            }
        }
        String peer = normalizeIp(request.getRemoteAddr());
        return peer == null ? "unknown" : peer;
    }

    private String singleValidIpHeader(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) {
            return null;
        }
        String value = values.nextElement();
        if (values.hasMoreElements()) {
            return null;
        }
        return normalizeIp(value);
    }

    private String normalizeIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.indexOf(',') >= 0 || candidate.indexOf('%') >= 0
                || !candidate.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (Exception invalidAddress) {
            return null;
        }
    }
}
