package com.hs.notification.scheduler;

import com.hs.notification.model.CaseWatchCheckpoint;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.CaseWatchCheckpointRepository;
import com.hs.notification.repository.TenantRepository;
import com.hs.notification.service.NotificationService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Polls HyperSense's case_tbl (a separate Postgres DB — see
 * hs-notification.case-tbl-datasource) for cases with id greater than a
 * persisted checkpoint, and fires NEW_CASE_ALERT_RULE for each one through
 * the existing rule-based send path. Off by default
 * (hs-notification.case-watch.enabled) and a no-op whenever the case_tbl
 * datasource or tenant-code isn't configured — same graceful-skip pattern as
 * PrRecordsExportService, so an incomplete config never breaks the platform.
 *
 * Each row's submitRuleBasedNotification call runs in its own independent
 * transaction (its default REQUIRED propagation creates a new one since this
 * method itself is deliberately not @Transactional) — that keeps one row's
 * failure from marking the whole poll cycle's transaction rollback-only,
 * which would otherwise wipe out every other row's send in the same batch
 * and the checkpoint advance along with it.
 */
@Component
public class CaseWatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(CaseWatchScheduler.class);
    private static final String RULE_CODE = "NEW_CASE_ALERT_RULE";

    private final boolean enabled;
    private final String tenantCode;
    private final HikariDataSource caseTblDataSource; // null when not configured

    private final TenantRepository tenantRepository;
    private final CaseWatchCheckpointRepository checkpointRepository;
    private final NotificationService notificationService;

    public CaseWatchScheduler(
            TenantRepository tenantRepository,
            CaseWatchCheckpointRepository checkpointRepository,
            NotificationService notificationService,
            @Value("${hs-notification.case-watch.enabled:false}") boolean enabled,
            @Value("${hs-notification.case-watch.tenant-code:}") String tenantCode,
            @Value("${hs-notification.case-tbl-datasource.url:}") String url,
            @Value("${hs-notification.case-tbl-datasource.username:}") String username,
            @Value("${hs-notification.case-tbl-datasource.password:}") String password,
            @Value("${hs-notification.case-tbl-datasource.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${hs-notification.case-tbl-datasource.socket-timeout-seconds:20}") int socketTimeoutSeconds) {

        this.tenantRepository = tenantRepository;
        this.checkpointRepository = checkpointRepository;
        this.notificationService = notificationService;
        this.enabled = enabled;
        this.tenantCode = tenantCode;

        if (url == null || url.isBlank() || username == null || username.isBlank()) {
            log.warn("hs-notification.case-tbl-datasource is not configured — the case-watch poller will skip every run");
            this.caseTblDataSource = null;
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("case-tbl-pool");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(connectTimeoutMs);
        // Best-effort secondary datasource — the app must still boot cleanly
        // even if case_tbl happens to be unreachable at startup.
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("connectTimeout", String.valueOf(Math.max(1, connectTimeoutMs / 1000)));
        config.addDataSourceProperty("socketTimeout", String.valueOf(socketTimeoutSeconds));
        this.caseTblDataSource = new HikariDataSource(config);
    }

    @PreDestroy
    public void shutdown() {
        if (caseTblDataSource != null) {
            caseTblDataSource.close();
        }
    }

    @Scheduled(fixedDelayString = "${hs-notification.case-watch.poll-interval-ms:60000}")
    public void poll() {
        if (!enabled) return;

        if (caseTblDataSource == null) {
            log.debug("case-watch poller is enabled but hs-notification.case-tbl-datasource is not configured — skipping");
            return;
        }
        if (tenantCode == null || tenantCode.isBlank()) {
            log.warn("case-watch poller is enabled but hs-notification.case-watch.tenant-code is not configured — skipping");
            return;
        }

        Optional<Tenant> tenantOpt = tenantRepository.findByTenantCode(tenantCode);
        if (tenantOpt.isEmpty()) {
            log.warn("case-watch poller: configured tenant-code '{}' not found — skipping", tenantCode);
            return;
        }
        Tenant tenant = tenantOpt.get();

        try (Connection connection = caseTblDataSource.getConnection()) {
            Optional<CaseWatchCheckpoint> existing = checkpointRepository.findFirstByOrderByCheckpointIdAsc();
            if (existing.isEmpty()) {
                seedCheckpoint(connection);
                return;
            }

            processNewCases(connection, existing.get(), tenant);

        } catch (Exception e) {
            log.error("case-watch poller: poll cycle failed: {}", e.getMessage(), e);
        }
    }

    private void processNewCases(Connection connection, CaseWatchCheckpoint checkpoint, Tenant tenant) throws Exception {
        long lastSeenCaseId = checkpoint.getLastSeenCaseId();
        long highestSeen = lastSeenCaseId;

        // TODO once real case_tbl access/schema is confirmed: if case_tbl has an
        // assigned-analyst/owner column, add it here and set context.put("case_owner_email", ...)
        // below — that's the key NotificationService.resolveCurrentUserRecipient reads
        // as the fallback for CURRENT_USER rules on this no-acting-user (scheduler) path.
        String sql = "SELECT id, case_template_name, pipeline_names, grouped_entity_key, " +
                "grouped_entity_value, severity, created_at FROM case_tbl WHERE id > ? ORDER BY id ASC";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, lastSeenCaseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long caseId = rs.getLong("id");
                    highestSeen = Math.max(highestSeen, caseId);

                    Map<String, Object> context = new HashMap<>();
                    context.put("case_id", caseId);
                    context.put("case_template_name", rs.getString("case_template_name"));
                    context.put("pipeline_names", rs.getString("pipeline_names"));
                    context.put("grouped_entity_key", rs.getString("grouped_entity_key"));
                    context.put("grouped_entity_value", rs.getString("grouped_entity_value"));
                    context.put("severity", rs.getString("severity"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) {
                        context.put("created_at", createdAt.toInstant().atOffset(ZoneOffset.UTC).toString());
                    }

                    try {
                        notificationService.submitRuleBasedNotification(
                                tenant, RULE_CODE, "NEW_CASE_" + caseId,
                                String.valueOf(caseId), "CASE_TBL_POLLER", context, null);
                    } catch (Exception e) {
                        log.error("case-watch poller: failed to submit notification for case_id={}: {}",
                                caseId, e.getMessage(), e);
                    }
                }
            }
        }

        if (highestSeen != lastSeenCaseId) {
            checkpoint.setLastSeenCaseId(highestSeen);
            checkpoint.setUpdatedAt(OffsetDateTime.now());
            checkpointRepository.save(checkpoint);
        }
    }

    private void seedCheckpoint(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT MAX(id) FROM case_tbl");
             ResultSet rs = ps.executeQuery()) {
            long maxId = 0;
            if (rs.next()) {
                maxId = rs.getLong(1);
            }
            CaseWatchCheckpoint checkpoint = new CaseWatchCheckpoint();
            checkpoint.setLastSeenCaseId(maxId);
            checkpoint.setUpdatedAt(OffsetDateTime.now());
            checkpointRepository.save(checkpoint);
            log.info("case-watch poller: seeded checkpoint to last_seen_case_id={} (existing backlog will not be sent)", maxId);
        } catch (Exception e) {
            log.error("case-watch poller: failed to seed checkpoint from case_tbl: {}", e.getMessage(), e);
        }
    }
}
