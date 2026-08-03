package com.hs.notification.service;

import com.hs.notification.model.NotificationObjectRegistry;
import com.hs.notification.repository.NotificationObjectRegistryRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Looks up NotifyRequest.sourceType against notification_object_registry
 * (V16 migration) so a caller's object type can resolve to an
 * AttachmentProvider key without already knowing attachmentOptions.providers.
 *
 * Exact-match only, deliberately: today's real sourceType values in the wild
 * (see docs/HS-Notify-API.postman_collection.json, V2 seed data) are
 * descriptive event strings — "PR_CLOSURE", "CASE_ESCALATION", "PR" — not the
 * bare object_type codes seeded here ("CASE", "PR"). A miss just means no
 * auto-routing kicks in for that sourceType (identical to today's behavior,
 * since no such routing exists yet at all) — it never guesses via prefix or
 * fuzzy matching, which would make routing decisions unauditable.
 */
@Service
public class ObjectRegistryResolver {

    private final NotificationObjectRegistryRepository repository;

    public ObjectRegistryResolver(NotificationObjectRegistryRepository repository) {
        this.repository = repository;
    }

    /** Empty when sourceType is blank, has no active registry row, or the row has no attachment_provider_key set. */
    public Optional<String> resolveAttachmentProviderKey(String sourceType) {
        return resolve(sourceType).map(NotificationObjectRegistry::getAttachmentProviderKey)
                .filter(key -> key != null && !key.isBlank());
    }

    public Optional<NotificationObjectRegistry> resolve(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return Optional.empty();
        }
        return repository.findByObjectTypeAndActiveTrue(sourceType);
    }
}
