package com.hs.notification.controller;

import com.hs.notification.model.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Lets an operator attach an arbitrary file to a manual/direct send (as opposed
 * to the rule-driven AttachmentService, which auto-fetches report PDFs). Stores
 * to local scratch storage and hands back a path that send-direct accepts.
 */
@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentUploadController {

    private final String storageDir;

    public AttachmentUploadController(
            @Value("${hs-notification.attachments.storage-path:${java.io.tmpdir}/hs-notification-attachments}") String storageDir) {
        this.storageDir = storageDir;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                       HttpServletRequest httpRequest) throws IOException {
        resolveTenant(httpRequest);
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String original = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment");
        String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedName = UUID.randomUUID() + "__" + safeName;

        Path dir = Path.of(storageDir);
        Files.createDirectories(dir);
        Path target = dir.resolve(storedName);
        file.transferTo(target);

        return ResponseEntity.ok(Map.of(
                "path", target.toAbsolutePath().toString(),
                "filename", safeName,
                "sizeBytes", file.getSize()
        ));
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");
        return tenant;
    }
}
