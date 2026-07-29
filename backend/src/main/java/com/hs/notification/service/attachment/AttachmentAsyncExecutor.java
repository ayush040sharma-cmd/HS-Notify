package com.hs.notification.service.attachment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * A separate bean (not a method on AttachmentOrchestrationService) is
 * required for @Async to actually take effect — Spring's proxy only
 * intercepts calls that come from a different bean, not self-invocation
 * within the same class. The app already has @EnableAsync
 * (NotificationPlatformApplication), so this reuses Spring's default
 * SimpleAsyncTaskExecutor rather than adding new infrastructure.
 */
@Service
public class AttachmentAsyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(AttachmentAsyncExecutor.class);

    public record Outcome(String providerKey, AttachmentResult result) {}

    @Async
    public CompletableFuture<Outcome> generate(AttachmentProvider provider, AttachmentContext context) {
        try {
            return CompletableFuture.completedFuture(new Outcome(provider.key(), provider.generate(context)));
        } catch (Exception e) {
            log.error("Attachment provider {} threw unexpectedly", provider.key(), e);
            return CompletableFuture.completedFuture(
                    new Outcome(provider.key(), AttachmentResult.failure("Unexpected error: " + e.getMessage())));
        }
    }
}
