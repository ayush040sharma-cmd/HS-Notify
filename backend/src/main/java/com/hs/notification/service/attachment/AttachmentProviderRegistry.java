package com.hs.notification.service.attachment;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Auto-collects every AttachmentProvider bean — mirrors how MailDispatchService builds its channel-sender map from a List<ChannelSender>. */
@Service
public class AttachmentProviderRegistry {

    private final Map<String, AttachmentProvider> providers;

    public AttachmentProviderRegistry(List<AttachmentProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(AttachmentProvider::key, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    public List<AttachmentProvider> all() {
        return List.copyOf(providers.values());
    }

    public Optional<AttachmentProvider> find(String key) {
        return Optional.ofNullable(providers.get(key));
    }
}
