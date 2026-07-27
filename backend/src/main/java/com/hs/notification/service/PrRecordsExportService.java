package com.hs.notification.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.util.regex.Pattern;

/**
 * Streams a PR-records CSV export from the "usage DB" — a separate Postgres
 * database holding system_pr_results_<catalog_id> tables, mirroring the
 * production bash script's
 *   COPY (SELECT * FROM system_pr_results_<catalog_id> WHERE case_id = <case_id>)
 *   TO STDOUT WITH CSV HEADER
 * This is deliberately not a Spring-managed DataSource bean — a second
 * unqualified DataSource bean would create ambiguity for JPA/Flyway/the
 * actuator health indicator, which all auto-wire DataSource by type. Owning
 * a private Hikari pool here keeps this datasource fully invisible to the
 * rest of the app's wiring and to /actuator/health.
 */
@Service
public class PrRecordsExportService {

    private static final Logger log = LoggerFactory.getLogger(PrRecordsExportService.class);

    // Restricts case_id/catalog_id to characters that can't break out of the
    // interpolated SQL — COPY doesn't support bind parameters, so the table
    // name and the WHERE value are both built into the SQL string by hand.
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final HikariDataSource dataSource;

    public PrRecordsExportService(
            @Value("${hs-notification.usage-datasource.url:}") String url,
            @Value("${hs-notification.usage-datasource.username:}") String username,
            @Value("${hs-notification.usage-datasource.password:}") String password,
            @Value("${hs-notification.usage-datasource.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${hs-notification.usage-datasource.socket-timeout-seconds:20}") int socketTimeoutSeconds) {

        if (url == null || url.isBlank() || username == null || username.isBlank()) {
            log.warn("hs-notification.usage-datasource is not configured — PR records CSV export will be " +
                    "skipped (with a graceful notice) for every send that requests it");
            this.dataSource = null;
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("usage-db-pool");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(connectTimeoutMs);
        // Best-effort secondary datasource — the app must still boot cleanly
        // even if the usage DB happens to be unreachable at startup.
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

    public record ExportResult(byte[] csvBytes, int rowCount, String failureReason) {
        public boolean success() {
            return failureReason == null;
        }
    }

    /**
     * Never throws — every failure mode (not configured, missing/invalid
     * context, unreachable DB, zero rows) comes back as a failureReason so the
     * caller can send the email without the attachment and record why.
     */
    public ExportResult export(Object caseIdRaw, Object catalogIdRaw) {
        if (dataSource == null) {
            return new ExportResult(null, 0, "usage DB is not configured (hs-notification.usage-datasource.url is unset)");
        }

        String caseId = caseIdRaw == null ? null : caseIdRaw.toString().trim();
        String catalogId = catalogIdRaw == null ? null : catalogIdRaw.toString().trim();

        if (caseId == null || caseId.isBlank()) {
            return new ExportResult(null, 0, "case_id missing from context");
        }
        if (catalogId == null || catalogId.isBlank()) {
            return new ExportResult(null, 0, "catalog_id missing from context");
        }
        if (!SAFE_IDENTIFIER.matcher(catalogId).matches()) {
            return new ExportResult(null, 0, "catalog_id has an unexpected format — refusing to build query");
        }
        if (!SAFE_IDENTIFIER.matcher(caseId).matches()) {
            return new ExportResult(null, 0, "case_id has an unexpected format — refusing to build query");
        }

        String sql = "COPY (SELECT * FROM system_pr_results_" + catalogId +
                " WHERE case_id = " + caseId + ") TO STDOUT WITH CSV HEADER";

        try (Connection connection = dataSource.getConnection()) {
            CopyManager copyManager = new CopyManager(connection.unwrap(BaseConnection.class));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long rowsCopied = copyManager.copyOut(sql, out);

            if (rowsCopied <= 0) {
                return new ExportResult(null, 0,
                        "no PR records found for case_id=" + caseId + ", catalog_id=" + catalogId);
            }
            return new ExportResult(out.toByteArray(), (int) rowsCopied, null);

        } catch (Exception e) {
            log.warn("PR records export failed for case_id={}, catalog_id={}: {}", caseId, catalogId, e.getMessage());
            return new ExportResult(null, 0, "usage DB query failed: " + e.getMessage());
        }
    }
}
