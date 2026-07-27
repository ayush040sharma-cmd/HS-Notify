package com.hs.notification.controller;

import com.hs.notification.dto.NotificationJobResponse;
import com.hs.notification.dto.SendCustomNotificationRequest;
import com.hs.notification.dto.SendCustomNotificationResponse;
import com.hs.notification.dto.SendDirectRequest;
import com.hs.notification.dto.SendNotificationRequest;
import com.hs.notification.model.NotificationJob;
import com.hs.notification.model.Tenant;
import com.hs.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/send-by-rule")
    public ResponseEntity<NotificationJobResponse> sendByRule(
            @Valid @RequestBody SendNotificationRequest request,
            HttpServletRequest httpRequest) {

        Tenant tenant = resolveTenant(httpRequest);
        NotificationJob job = notificationService.submitRuleBasedNotification(
                tenant,
                request.ruleCode(),
                request.idempotencyKey(),
                request.sourceReference(),
                request.sourceType(),
                request.context() != null ? request.context() : java.util.Map.of(),
                request.recipientOverride()
        );
        return ResponseEntity.ok(NotificationJobResponse.from(job));
    }

    @PostMapping("/send-direct")
    public ResponseEntity<NotificationJobResponse> sendDirect(
            @Valid @RequestBody SendDirectRequest request,
            HttpServletRequest httpRequest) {

        Tenant tenant = resolveTenant(httpRequest);
        String actor = httpRequest.getRemoteUser() != null ? httpRequest.getRemoteUser() : "ui-user";

        NotificationJob job = notificationService.submitDirectNotification(
                tenant, request.to(), request.cc(), request.templateCode(), request.context(),
                request.subject(), request.htmlBody(), request.attachmentPath(), actor);
        return ResponseEntity.ok(NotificationJobResponse.from(job));
    }

    @PostMapping("/send-custom")
    public ResponseEntity<SendCustomNotificationResponse> sendCustom(
            @Valid @RequestBody SendCustomNotificationRequest request,
            HttpServletRequest httpRequest) {

        Tenant tenant = resolveTenant(httpRequest);
        String actor = httpRequest.getRemoteUser() != null ? httpRequest.getRemoteUser() : "ui-user";

        NotificationService.CustomSendResult result =
                notificationService.submitCustomNotification(tenant, request, actor);
        return ResponseEntity.ok(new SendCustomNotificationResponse(
                NotificationJobResponse.from(result.job()), result.notices()));
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) {
            throw new IllegalStateException("Tenant not resolved - ApiKeyAuthFilter should have set this");
        }
        return tenant;
    }
}
