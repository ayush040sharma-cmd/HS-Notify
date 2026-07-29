package com.hs.notification.service.attachment;

import com.hs.notification.model.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Runs every requested provider concurrently (via AttachmentAsyncExecutor),
 * then bundles the results: one success -> attach directly, multiple ->
 * zip them together, none -> no attachment. Never blocks the send — a
 * provider failure just becomes a notice, same contract every other
 * attachment path in this codebase already follows.
 */
@Service
public class AttachmentOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentOrchestrationService.class);

    private final AttachmentProviderRegistry registry;
    private final AttachmentAsyncExecutor asyncExecutor;
    private final AttachmentStorageWriter storageWriter;

    public AttachmentOrchestrationService(AttachmentProviderRegistry registry,
                                           AttachmentAsyncExecutor asyncExecutor,
                                           AttachmentStorageWriter storageWriter) {
        this.registry = registry;
        this.asyncExecutor = asyncExecutor;
        this.storageWriter = storageWriter;
    }

    public record BundleResult(String path, List<String> notices) {}

    public BundleResult generateBundle(List<String> providerKeys, Tenant tenant, Map<String, Object> payload) {
        AttachmentContext context = new AttachmentContext(tenant, payload);

        List<CompletableFuture<AttachmentAsyncExecutor.Outcome>> futures = providerKeys.stream()
                .map(key -> registry.find(key)
                        .map(provider -> asyncExecutor.generate(provider, context))
                        .orElseGet(() -> CompletableFuture.completedFuture(
                                new AttachmentAsyncExecutor.Outcome(key, AttachmentResult.failure("Unknown provider: " + key)))))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<AttachmentAsyncExecutor.Outcome> outcomes = futures.stream().map(CompletableFuture::join).toList();

        List<String> notices = new ArrayList<>();
        List<AttachmentAsyncExecutor.Outcome> succeeded = new ArrayList<>();
        for (var outcome : outcomes) {
            if (outcome.result().success()) {
                succeeded.add(outcome);
                notices.add(outcome.providerKey() + " attached (" + outcome.result().filename() + ").");
            } else {
                notices.add(outcome.providerKey() + " not attached: " + outcome.result().failureReason());
            }
        }

        if (succeeded.isEmpty()) {
            return new BundleResult(null, notices);
        }

        try {
            if (succeeded.size() == 1) {
                var only = succeeded.get(0).result();
                String path = storageWriter.write(only.bytes(), only.filename());
                return new BundleResult(path, notices);
            }
            byte[] zipBytes = zip(succeeded);
            String path = storageWriter.write(zipBytes, "attachments_" + tenant.getTenantCode() + ".zip");
            return new BundleResult(path, notices);
        } catch (IOException e) {
            log.warn("Failed to write attachment bundle to storage: {}", e.getMessage());
            notices.add("Failed to write attachment bundle: " + e.getMessage());
            return new BundleResult(null, notices);
        }
    }

    private byte[] zip(List<AttachmentAsyncExecutor.Outcome> succeeded) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
            for (var outcome : succeeded) {
                zos.putNextEntry(new ZipEntry(outcome.result().filename()));
                zos.write(outcome.result().bytes());
                zos.closeEntry();
            }
        }
        return buffer.toByteArray();
    }
}
