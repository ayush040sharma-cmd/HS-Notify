package com.hs.notification.config;

import com.hs.notification.repository.ApiKeyRepository;
import com.hs.notification.repository.TenantRepository;
import com.hs.notification.security.AdminJwtAuthFilter;
import com.hs.notification.security.AdminJwtService;
import com.hs.notification.security.ApiKeyAuthFilter;
import com.hs.notification.security.ApiKeyResolver;
import com.hs.notification.security.KeyLookup;
import com.hs.notification.security.LookupTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TenantRepository tenantRepository;
    private final ApiKeyResolver apiKeyResolver;
    private final AdminJwtService adminJwtService;
    private final String lookupToken;

    public SecurityConfig(TenantRepository tenantRepository, ApiKeyResolver apiKeyResolver,
                           AdminJwtService adminJwtService,
                           @Value("${hs-notification.security.lookup-token}") String lookupToken) {
        this.tenantRepository = tenantRepository;
        this.apiKeyResolver = apiKeyResolver;
        this.adminJwtService = adminJwtService;
        this.lookupToken = lookupToken;
    }

    /** Bridges the narrow KeyLookup seam to the full repository for production use. */
    @Bean
    public static KeyLookup keyLookup(ApiKeyRepository apiKeyRepository) {
        return apiKeyRepository::findByKeyPrefixAndRevokedFalse;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/docs/**", "/api-docs/**", "/swagger-ui/**",
                        "/api/v1/admin/**", "/api/v1/auth/**", "/error").permitAll()
                // Unauthenticated read-only lookups for HyperSense's PAS analyst action
                // screen to populate a dropdown. No tenant/API-key context is available
                // here — see ApiKeyAuthFilter/AdminJwtAuthFilter for the matching skip.
                // /api/v1/actions (+ /{code}/schema) are the action-picker + dynamic
                // form/checkbox discovery calls for the unified /api/v1/notify flow —
                // same machine-caller need as templates/active and rules/active.
                .requestMatchers(HttpMethod.GET, "/api/v1/templates/active", "/api/v1/rules/active",
                        "/api/v1/actions", "/api/v1/actions/*/schema").permitAll()

                // --- Phase 5 RBAC: tiered mutation gates on top of "any logged-in
                // role can view everything" (the final .anyRequest().authenticated()
                // below). VIEWER never matches any of these -> read-only. Ordering
                // matters: specific approve/submit paths must precede the general
                // create/edit matchers they'd otherwise also satisfy. ---

                // ADMIN-only: platform/tenant configuration, not case-work.
                .requestMatchers(HttpMethod.POST, "/api/v1/users").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/smtp-config").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/whatsapp-config").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/escalation-config").hasRole("ADMIN")

                // MANAGER/RAFM_HEAD/ADMIN: maker-checker approval + delete of config objects.
                .requestMatchers(HttpMethod.POST, "/api/v1/rules/*/approve").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/v1/rules/*/disable").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/v1/templates/*/approve").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/rules/**").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/templates/**").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/actions/**").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/form-schemas/**").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/attachment-schemas/**").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER")

                // ANALYST and above (i.e. everyone but VIEWER): create/edit config,
                // submit-for-review (maker side of maker-checker), operate the job queue.
                .requestMatchers(HttpMethod.POST, "/api/v1/rules/*/submit-for-review").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/rules").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.PUT, "/api/v1/rules/**").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/templates").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.PUT, "/api/v1/templates/**").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/actions").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.PUT, "/api/v1/actions/**").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/form-schemas").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.PUT, "/api/v1/form-schemas/**").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/attachment-schemas").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.PUT, "/api/v1/attachment-schemas/**").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/jobs/*/requeue").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/jobs/*/cancel").hasAnyRole("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST")

                .anyRequest().authenticated()
            )
            .addFilterBefore(new LookupTokenFilter(lookupToken), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new ApiKeyAuthFilter(tenantRepository, apiKeyResolver),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new AdminJwtAuthFilter(adminJwtService), ApiKeyAuthFilter.class);

        return http.build();
    }
}
