package com.hs.notification.service.directory;

import com.hs.notification.model.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Audit-trail-only classification of a recipient email as internal/external
 * (see hs-notification.recipient-classification.internal-domains). Never
 * gates send behavior — the payoff is HS Notify's own frontend eventually
 * using this to offer a picker instead of a free-text box for internal
 * recipients; that's a follow-up frontend task, not implemented here.
 */
@Service
public class RecipientClassifier {

    private static final Logger log = LoggerFactory.getLogger(RecipientClassifier.class);

    private final Set<String> internalDomains;
    private final UserDirectoryResolver userDirectoryResolver;

    public RecipientClassifier(
            @Value("${hs-notification.recipient-classification.internal-domains}") String internalDomainsCsv,
            UserDirectoryResolver userDirectoryResolver) {
        this.internalDomains = Arrays.stream(internalDomainsCsv.split(","))
                .map(String::trim)
                .map(d -> d.toLowerCase(Locale.ROOT))
                .filter(d -> !d.isBlank())
                .collect(Collectors.toSet());
        this.userDirectoryResolver = userDirectoryResolver;
    }

    /**
     * Logs the classification and returns nothing — callers use this purely
     * for the audit trail, not to influence whether/how the send proceeds.
     * A no-op for anything that isn't email-shaped (e.g. a token that already
     * failed recipient resolution never reaches here).
     *
     * @param tenant     the resolved tenant for this send (never client-supplied)
     * @param actionCode the notification_action/rule code driving this send
     * @param slot       which recipient field this address came from — "TO"/"CC"/"BCC"
     * @param email      the fully-resolved recipient address to classify
     */
    public void classifyAndLog(Tenant tenant, String actionCode, String slot, String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return;
        }
        String domain = email.substring(at + 1).toLowerCase(Locale.ROOT);
        boolean internal = internalDomains.contains(domain);

        boolean directoryMatch = internal && userDirectoryResolver.existsByEmail(email);
        log.info("recipient {} classified as {}, directory-match={}, tenant={}, action={}, slot={}",
                email, internal ? "internal" : "external", directoryMatch,
                tenant.getTenantCode(), actionCode, slot);

        if (internal && !directoryMatch) {
            log.warn("recipient {} has an internal domain ({}) but was not found in the user directory " +
                    "(tenant={}, action={}, slot={})", email, domain, tenant.getTenantCode(), actionCode, slot);
        }
    }
}
