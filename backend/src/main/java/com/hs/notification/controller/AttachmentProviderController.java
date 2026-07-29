package com.hs.notification.controller;

import com.hs.notification.dto.AttachmentProviderResponse;
import com.hs.notification.service.attachment.AttachmentProviderRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only registry listing — lets an admin UI or the wizard show which attachment providers exist and are actually usable right now. */
@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentProviderController {

    private final AttachmentProviderRegistry registry;

    public AttachmentProviderController(AttachmentProviderRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/providers")
    public ResponseEntity<List<AttachmentProviderResponse>> providers() {
        return ResponseEntity.ok(registry.all().stream().map(AttachmentProviderResponse::from).toList());
    }
}
