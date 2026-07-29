package com.hs.notification.security;

import com.hs.notification.model.Tenant;
import com.hs.notification.repository.TenantRepository;
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
 * Authenticates inbound calls from the HS workflow engine (Alarm/Case/
 * Analyst closure actions) via a per-tenant API key header. This is the
 * minimum viable security control; production deployments should replace
 * this with mutual TLS or short-lived JWTs issued by HS's own auth service
 * (see notes in the Claude Code prompt for hardening this further).
 *
 * On success, resolves the tenant from the key and stores it in the request
 * attributes for downstream controllers - this is how every request gets
 * correctly scoped to its tenant without trusting a client-supplied tenant id.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-HS-API-Key";

    private final TenantRepository tenantRepository;
    private final ApiKeyResolver apiKeyResolver;

    public ApiKeyAuthFilter(TenantRepository tenantRepository, ApiKeyResolver apiKeyResolver) {
        this.tenantRepository = tenantRepository;
        this.apiKeyResolver = apiKeyResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        // Allow health/docs endpoints through without auth.
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/docs") || path.startsWith("/api-docs")
                || path.startsWith("/api/v1/admin") || path.startsWith("/api/v1/auth") || path.startsWith("/error")) {
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

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing " + API_KEY_HEADER + " header");
            return;
        }

        Optional<String> tenantCode = apiKeyResolver.resolveTenantCode(apiKey);
        if (tenantCode.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }

        Optional<Tenant> tenant = tenantRepository.findByTenantCode(tenantCode.get());
        if (tenant.isEmpty() || !tenant.get().isActive()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant not found or inactive");
            return;
        }

        request.setAttribute("resolvedTenant", tenant.get());

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                tenantCode.get(), null, List.of(new SimpleGrantedAuthority("ROLE_HS_WORKFLOW")));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }
}
