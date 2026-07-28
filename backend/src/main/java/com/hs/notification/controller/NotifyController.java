package com.hs.notification.controller;

import com.hs.notification.dto.NotificationJobResponse;
import com.hs.notification.dto.NotifyRequest;
import com.hs.notification.dto.NotifyResponse;
import com.hs.notification.model.Tenant;
import com.hs.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The unified send endpoint (Phase 3 of the metadata-driven platform
 * rollout) — action resolves against notification_rule first, then the
 * notification_action registry (see NotificationService.notify).
 * send-by-rule/send-direct/send-custom (NotificationController) remain as
 * thin, backward-compatible wrappers around the same underlying engine
 * rather than being removed. Deliberately its own top-level controller, not
 * nested under NotificationController's /api/v1/notifications prefix — the
 * target path is /api/v1/notify, a sibling, not a sub-resource. Exempted
 * from the admin JWT requirement the same way /api/v1/notifications/** is
 * (see AdminJwtAuthFilter) since this is a machine/HyperSense-facing
 * endpoint authenticated by tenant API key alone.
 */
@RestController
public class NotifyController {

    private final NotificationService notificationService;

    public NotifyController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/api/v1/notify")
    public ResponseEntity<NotifyResponse> notify(
            @Valid @RequestBody NotifyRequest request,
            HttpServletRequest httpRequest) {

        Tenant tenant = resolveTenant(httpRequest);
        String actor = httpRequest.getRemoteUser() != null ? httpRequest.getRemoteUser() : "ui-user";

        NotificationService.NotifyResult result = notificationService.notify(tenant, request, actor);
        return ResponseEntity.ok(new NotifyResponse(NotificationJobResponse.from(result.job()), result.notices()));
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) {
            throw new IllegalStateException("Tenant not resolved - ApiKeyAuthFilter should have set this");
        }
        return tenant;
    }
}
