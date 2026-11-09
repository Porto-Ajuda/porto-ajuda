package com.portoajuda.aplicacao_osc.segurity;

import com.portoajuda.aplicacao_osc.admin.AdminAuthenticationDetails;
import com.portoajuda.aplicacao_osc.controller.AdminSessionCookie;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService;
import com.portoajuda.aplicacao_osc.service.AdminPrivilegedSessionService.AuthenticatedAdmin;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class AdminSessionAuthFilter extends OncePerRequestFilter {

    private static final String ADMIN_SESSION_AUTHORITY = "ADMIN_SESSION";
    public static final String AUTHENTICATED_ADMIN_ATTRIBUTE =
            AdminSessionAuthFilter.class.getName() + ".AUTHENTICATED_ADMIN";

    private final AdminPrivilegedSessionService sessionService;
    private final ClientIpResolver clientIpResolver;
    private final AdminSessionCookie sessionCookie;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        if (!"/admin".equals(path) && !path.startsWith("/admin/")) {
            return true;
        }
        if ("/admin/auth/logout".equals(path)) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return "/admin/auth/challenges".equals(path)
                || path.matches("^/admin/auth/challenges/[0-9a-fA-F-]{36}/verify$");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // Admin requests never inherit a principal created by the ordinary JWT filter.
        SecurityContextHolder.clearContext();
        String rawCredential = getCookie(request, AdminSessionCookie.NAME);
        AuthenticatedAdmin authenticated = sessionService
                .autenticar(rawCredential, clientIpResolver.resolve(request))
                .orElse(null);

        if (authenticated != null) {
            request.setAttribute(AUTHENTICATED_ADMIN_ATTRIBUTE, authenticated);
        }

        if (authenticated == null) {
            if (rawCredential != null && !rawCredential.isBlank()) {
                response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.clear().toString());
            }
        } else if (SecurityContextHolder.getContext().getAuthentication() == null) {
            List<GrantedAuthority> authorities = new ArrayList<>(authenticated.usuario().getAuthorities());
            authorities.add(new SimpleGrantedAuthority(ADMIN_SESSION_AUTHORITY));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    authenticated.usuario(), null, authorities);
            authentication.setDetails(new AdminAuthenticationDetails(authenticated.sessionId()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
