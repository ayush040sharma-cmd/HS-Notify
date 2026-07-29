package com.hs.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gates the unauthenticated PAS/HyperSense dropdown lookups
 * (GET /api/v1/templates/active, GET /api/v1/rules/active, GET
 * /api/v1/actions, GET /api/v1/actions/{code}/schema) behind a single
 * static shared-secret header — not full auth, just enough that these
 * endpoints aren't sitting completely open on the network. Every other path
 * passes through untouched; ApiKeyAuthFilter/AdminJwtAuthFilter/SecurityConfig
 * each carry their own matching skip for these paths.
 */
public class LookupTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "X-HS-Lookup-Token";

    private final String expectedToken;

    public LookupTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean isLookupEndpoint = "GET".equals(request.getMethod())
                && ("/api/v1/templates/active".equals(path) || "/api/v1/rules/active".equals(path)
                    || "/api/v1/actions".equals(path) || path.matches("/api/v1/actions/[^/]+/schema"));

        if (!isLookupEndpoint) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || !token.equals(expectedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid " + TOKEN_HEADER + " header");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
