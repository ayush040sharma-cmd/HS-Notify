package com.hs.notification.service.directory;

import java.util.Optional;

/**
 * Resolves a HyperSense username (e.g. "ayush.sharma") to a real email
 * address. See HS_NOTIFICATION_V2_METADATA_DESIGN.md — "Internal user/email
 * directory". The single implementation today (AppMonitoringUserDirectoryResolver)
 * queries a same-cluster SQL mirror; kept as an interface so a future
 * Keycloak Admin API–backed implementation (or a second mirror) can replace
 * or supplement it without touching call sites.
 */
public interface UserDirectoryResolver {

    /**
     * @return the user's active email, or empty if the username isn't found,
     * isn't active, or the resolver isn't configured. Never throws for a
     * routine miss — callers should fall through to their next resolution
     * tier (e.g. domain-guess synthesis).
     */
    Optional<String> resolveEmail(String username);

    /**
     * Reverse lookup: does an active user with this email exist in the
     * directory? Same never-throws, false-when-unconfigured contract as
     * {@link #resolveEmail(String)} — a miss is routine, not exceptional.
     */
    boolean existsByEmail(String email);
}
