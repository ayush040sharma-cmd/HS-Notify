package com.hs.notification.controller;

import com.hs.notification.dto.SmtpConfigResponse;
import com.hs.notification.dto.UpdateSmtpConfigRequest;
import com.hs.notification.model.SmtpConfig;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.SmtpConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/smtp-config")
public class SmtpConfigController {

    private final SmtpConfigRepository smtpConfigRepository;
    private final String defaultHost;
    private final int defaultPort;

    public SmtpConfigController(SmtpConfigRepository smtpConfigRepository,
                                @Value("${spring.mail.host}") String defaultHost,
                                @Value("${spring.mail.port}") int defaultPort) {
        this.smtpConfigRepository = smtpConfigRepository;
        this.defaultHost = defaultHost;
        this.defaultPort = defaultPort;
    }

    @GetMapping
    public ResponseEntity<SmtpConfigResponse> get(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        SmtpConfig config = smtpConfigRepository.findByTenant_TenantIdAndActiveTrue(tenant.getTenantId())
                .orElseGet(() -> defaultConfig(tenant));
        return ResponseEntity.ok(SmtpConfigResponse.from(config));
    }

    @PutMapping
    public ResponseEntity<SmtpConfigResponse> update(@RequestBody UpdateSmtpConfigRequest request,
                                                     HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        SmtpConfig config = smtpConfigRepository.findByTenant_TenantIdAndActiveTrue(tenant.getTenantId())
                .orElseGet(() -> defaultConfig(tenant));

        if (request.host() != null) config.setHost(request.host());
        if (request.port() != null) config.setPort(request.port());
        if (request.username() != null) config.setUsername(request.username());
        if (request.useTls() != null) config.setUseTls(request.useTls());
        if (request.fromName() != null) config.setFromDisplayName(request.fromName());
        if (request.fromEmail() != null) config.setFromAddress(request.fromEmail());
        if (request.maxPerMinute() != null) config.setMaxPerMinute(request.maxPerMinute());

        smtpConfigRepository.save(config);
        return ResponseEntity.ok(SmtpConfigResponse.from(config));
    }

    private SmtpConfig defaultConfig(Tenant tenant) {
        SmtpConfig config = new SmtpConfig();
        config.setTenant(tenant);
        config.setHost(defaultHost);
        config.setPort(defaultPort);
        config.setUseTls(false);
        config.setFromAddress("no-reply@hs-notify.internal");
        config.setFromDisplayName("HyperSense Notifications");
        config.setSecretRef("none");
        config.setMaxPerMinute(60);
        config.setActive(true);
        return config;
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");
        return tenant;
    }
}
