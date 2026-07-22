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
                .requestMatchers(HttpMethod.GET, "/api/v1/templates/active", "/api/v1/rules/active").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new LookupTokenFilter(lookupToken), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new ApiKeyAuthFilter(tenantRepository, apiKeyResolver),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new AdminJwtAuthFilter(adminJwtService), ApiKeyAuthFilter.class);

        return http.build();
    }
}
