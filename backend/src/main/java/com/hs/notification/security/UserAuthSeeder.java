package com.hs.notification.security;

import com.hs.notification.model.AppUser;
import com.hs.notification.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On first startup, if the configured admin-login username's app_user row
 * has no password_hash yet, hashes hs-notification.security.admin-login.password
 * into it — same bootstrap pattern as ApiKeySeeder. Grandfathers the existing
 * admin/admin123 access unchanged once app_user.password_hash became the
 * real credential store (Phase 5 RBAC); every other account's password is
 * set at creation time via POST /api/v1/users instead.
 */
@Component
public class UserAuthSeeder {

    private static final Logger log = LoggerFactory.getLogger(UserAuthSeeder.class);

    private final AppUserRepository appUserRepository;
    private final String adminUsername;
    private final String adminPassword;

    public UserAuthSeeder(AppUserRepository appUserRepository,
                          @Value("${hs-notification.security.admin-login.username}") String adminUsername,
                          @Value("${hs-notification.security.admin-login.password}") String adminPassword) {
        this.appUserRepository = appUserRepository;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedAdminPassword() {
        appUserRepository.findByUsername(adminUsername).ifPresent(user -> {
            if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
                user.setPasswordHash(ApiKeyResolver.BCRYPT.encode(adminPassword));
                if (!"ADMIN".equals(user.getRole())) {
                    user.setRole("ADMIN");
                }
                appUserRepository.save(user);
                log.info("Seeded password for admin-login user '{}'", adminUsername);
            }
        });
    }
}
