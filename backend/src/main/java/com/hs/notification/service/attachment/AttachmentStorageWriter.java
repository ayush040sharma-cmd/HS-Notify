package com.hs.notification.service.attachment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Same UUID__displayName storage convention AttachmentUploadController uses, so EmailChannelSender derives the right filename. */
@Component
public class AttachmentStorageWriter {

    private final String storageDir;

    public AttachmentStorageWriter(
            @Value("${hs-notification.attachments.storage-path:${java.io.tmpdir}/hs-notification-attachments}") String storageDir) {
        this.storageDir = storageDir;
    }

    public String write(byte[] content, String displayName) throws IOException {
        String safeName = displayName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedName = UUID.randomUUID() + "__" + safeName;
        Path dir = Path.of(storageDir);
        Files.createDirectories(dir);
        Path target = dir.resolve(storedName);
        Files.write(target, content);
        return target.toAbsolutePath().toString();
    }
}
