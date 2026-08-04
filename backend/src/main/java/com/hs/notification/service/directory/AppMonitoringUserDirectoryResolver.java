package com.hs.notification.service.directory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/**
 * Queries appmonitoringmetricservice.users — a same-cluster, SQL-reachable
 * mirror of HyperSense's login directory (see
 * HS_NOTIFICATION_V2_METADATA_DESIGN.md — "Internal user/email directory").
 * Verified against real case_tbl.current_assigned_user values: 10 of 14
 * real usernames matched with correct, non-guessable emails, proving the
 * username+"@"+domain synthesis it replaces was already wrong for real users
 * (e.g. "pawan" -> pawan.tiwari@subex.com, not pawan@subex.com).
 *
 * Same graceful-skip pattern as CaseWatchScheduler's case_tbl datasource — a
 * blank/unreachable config never breaks the platform, callers just fall
 * through to their next resolution tier.
 */
@Component
public class AppMonitoringUserDirectoryResolver implements UserDirectoryResolver {

    private static final Logger log = LoggerFactory.getLogger(AppMonitoringUserDirectoryResolver.class);

    private final HikariDataSource dataSource; // null when not configured

    public AppMonitoringUserDirectoryResolver(
            @Value("${hs-notification.user-directory-datasource.url:}") String url,
            @Value("${hs-notification.user-directory-datasource.username:}") String username,
            @Value("${hs-notification.user-directory-datasource.password:}") String password,
            @Value("${hs-notification.user-directory-datasource.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${hs-notification.user-directory-datasource.socket-timeout-seconds:20}") int socketTimeoutSeconds) {

        if (url == null || url.isBlank() || username == null || username.isBlank()) {
            log.warn("hs-notification.user-directory-datasource is not configured — internal-user email " +
                    "resolution will fall through to domain-guess synthesis for every username");
            this.dataSource = null;
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("user-directory-pool");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(connectTimeoutMs);
        // Best-effort secondary datasource — the app must still boot cleanly
        // even if this happens to be unreachable at startup.
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("connectTimeout", String.valueOf(Math.max(1, connectTimeoutMs / 1000)));
        config.addDataSourceProperty("socketTimeout", String.valueOf(socketTimeoutSeconds));
        this.dataSource = new HikariDataSource(config);
    }

    @PreDestroy
    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public Optional<String> resolveEmail(String username) {
        if (dataSource == null || username == null || username.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT email_id FROM users WHERE user_name = ? AND status = 'active'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String email = rs.getString("email_id");
                    if (email != null && !email.isBlank()) {
                        return Optional.of(email);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("user-directory lookup failed for username={}: {}", username, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public boolean existsByEmail(String email) {
        if (dataSource == null || email == null || email.isBlank()) {
            return false;
        }

        String sql = "SELECT 1 FROM users WHERE email_id = ? AND status = 'active'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.warn("user-directory reverse lookup failed for email={}: {}", email, e.getMessage());
            return false;
        }
    }
}
