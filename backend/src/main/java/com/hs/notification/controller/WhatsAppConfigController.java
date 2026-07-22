package com.hs.notification.controller;

import com.hs.notification.dto.UpdateWhatsAppConfigRequest;
import com.hs.notification.dto.WhatsAppConfigResponse;
import com.hs.notification.model.Tenant;
import com.hs.notification.model.WhatsAppConfig;
import com.hs.notification.repository.WhatsAppConfigRepository;
import com.hs.notification.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/whatsapp-config")
public class WhatsAppConfigController {

    private final WhatsAppConfigRepository whatsAppConfigRepository;
    private final AuditService auditService;

    public WhatsAppConfigController(WhatsAppConfigRepository whatsAppConfigRepository,
                                    AuditService auditService) {
        this.whatsAppConfigRepository = whatsAppConfigRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<WhatsAppConfigResponse> get(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        WhatsAppConfig config = whatsAppConfigRepository.findByTenant_TenantIdAndActiveTrue(tenant.getTenantId())
                .orElseGet(() -> defaultConfig(tenant));
        return ResponseEntity.ok(WhatsAppConfigResponse.from(config));
    }

    @PutMapping
    public ResponseEntity<WhatsAppConfigResponse> update(@RequestBody UpdateWhatsAppConfigRequest request,
                                                         HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        WhatsAppConfig config = whatsAppConfigRepository.findByTenant_TenantIdAndActiveTrue(tenant.getTenantId())
                .orElseGet(() -> defaultConfig(tenant));

        if (request.businessAccountId() != null) config.setBusinessAccountId(request.businessAccountId());
        if (request.phoneNumberId() != null) config.setPhoneNumberId(request.phoneNumberId());
        if (request.webhookUrl() != null) config.setWebhookUrl(request.webhookUrl());
        // Blank apiKey means "leave the currently saved key alone" — the GET response never
        // echoes the real value back, so the form field is blank unless the admin types a
        // replacement.
        if (request.apiKey() != null && !request.apiKey().isBlank()) config.setApiKey(request.apiKey());

        whatsAppConfigRepository.save(config);
        auditService.log(tenant, null, null, "WHATSAPP_CONFIG_CHANGED",
                "WhatsApp Business API configuration updated", actorOf(httpRequest), null);
        return ResponseEntity.ok(WhatsAppConfigResponse.from(config));
    }

    private WhatsAppConfig defaultConfig(Tenant tenant) {
        WhatsAppConfig config = new WhatsAppConfig();
        config.setTenant(tenant);
        config.setActive(true);
        return config;
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");
        return tenant;
    }

    private String actorOf(HttpServletRequest request) {
        return request.getRemoteUser() != null ? request.getRemoteUser() : "operator";
    }
}
