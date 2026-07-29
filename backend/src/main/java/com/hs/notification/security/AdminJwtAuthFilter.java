package com.hs.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Gates the human-facing dashboard endpoints behind the admin login session.
 * Runs after ApiKeyAuthFilter (which resolves the tenant and is left
 * untouched) and additionally requires a valid Bearer JWT for everything
 * except:
 *  - /api/v1/notifications/**, /api/v1/notify — the HS workflow-engine
 *    trigger endpoints; authenticated purely via tenant API key, called by
 *    machines, not by a logged-in operator, and also reused by the
 *    dashboard's own "Send Test Notification" page which already carries
 *    the API key.
 *  - /api/v1/auth/**           — the login endpoint itself.
 *  - /api/v1/admin/**          — already separately gated by X-Admin-Token.
 *  - /actuator, /docs, /api-docs, /swagger-ui — ops/health/docs surfaces.
 *  - GET /api/v1/templates/active, GET /api/v1/rules/active — unauthenticated
 *    dropdown lookups for HyperSense's PAS analyst action screen.
 *  - GET /api/v1/actions, GET /api/v1/actions/{code}/schema — same, for the
 *    action picker + dynamic form/checkbox discovery behind /api/v1/notify.
 */
public class AdminJwtAuthFilter extends OncePerRequestFilter {

    private final AdminJwtService jwtService;

    public AdminJwtAuthFilter(AdminJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/docs") || path.startsWith("/api-docs")
                || path.startsWith("/swagger-ui") || path.startsWith("/api/v1/admin")
                || path.startsWith("/api/v1/auth") || path.startsWith("/api/v1/notifications")
                || path.equals("/api/v1/notify")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Unauthenticated PAS dropdown lookups — see SecurityConfig for the matching permitAll.
        if ("GET".equals(request.getMethod())
                && ("/api/v1/templates/active".equals(path) || "/api/v1/rules/active".equals(path)
                    || "/api/v1/actions".equals(path) || path.matches("/api/v1/actions/[^/]+/schema"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;

        Optional<AdminJwtService.SessionClaims> claims = token != null ? jwtService.validateAndGetClaims(token) : Optional.empty();
        if (claims.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid admin session token");
            return;
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                claims.get().username(), null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.get().role())));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }
}
