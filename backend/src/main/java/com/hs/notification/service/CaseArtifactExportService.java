package com.hs.notification.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Fetches case-evidence files (case_doc_artifact) for a case, via the chain
 * verified in HS_NOTIFICATION_V2_METADATA_DESIGN.md:
 *   case_id -> case_workstep.case_id -> case_workstep.id
 *           -> case_artifact.case_workstep_id -> case_artifact.id
 *           -> case_doc_artifact.case_artifact_id -> document bytea
 * case_artifact itself is metadata only (findings/properties), not the file
 * table — case_doc_artifact holds the actual bytes. Reuses
 * hs-notification.case-tbl-datasource (the same casemanagement DB
 * CaseWatchScheduler already polls), but owns a private Hikari pool rather
 * than sharing one — see PrRecordsExportService for why a second
 * Spring-managed DataSource bean would create ambiguity for JPA/Flyway/the
 * actuator health indicator.
 *
 * Current volume is thin (12 rows in case_doc_artifact DB-wide as of the
 * schema verification this was built against) — most cases will have no
 * evidence files, which is a normal "not attached" outcome, not a bug.
 */
@Service
public class CaseArtifactExportService {

    private static final Logger log = LoggerFactory.getLogger(CaseArtifactExportService.class);

    private static final String SELECT_DOCS_SQL =
            "SELECT cda.name, cda.sub_type, cda.document " +
            "FROM case_doc_artifact cda " +
            "JOIN case_artifact ca ON ca.id = cda.case_artifact_id " +
            "JOIN case_workstep cw ON cw.id = ca.case_workstep_id " +
            "WHERE cw.case_id = ? " +
            "ORDER BY cda.id";

    private final HikariDataSource dataSource;

    public CaseArtifactExportService(
            @Value("${hs-notification.case-tbl-datasource.url:}") String url,
            @Value("${hs-notification.case-tbl-datasource.username:}") String username,
            @Value("${hs-notification.case-tbl-datasource.password:}") String password,
            @Value("${hs-notification.case-tbl-datasource.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${hs-notification.case-tbl-datasource.socket-timeout-seconds:20}") int socketTimeoutSeconds) {

        if (url == null || url.isBlank() || username == null || username.isBlank()) {
            log.warn("hs-notification.case-tbl-datasource is not configured — case-evidence attachment " +
                    "export will be skipped (with a graceful notice) for every send that requests it");
            this.dataSource = null;
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("case-artifact-pool");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(connectTimeoutMs);
        // Best-effort secondary datasource — the app must still boot cleanly
        // even if the casemanagement DB happens to be unreachable at startup.
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

    public boolean isConfigured() {
        return dataSource != null;
    }

    public record ExportResult(byte[] bytes, String filename, String contentType, String failureReason) {
        public boolean success() {
            return failureReason == null;
        }
    }

    private record Doc(String name, String subType, byte[] bytes) {}

    /**
     * Never throws — every failure mode (not configured, missing/invalid
     * case_id, unreachable DB, zero files) comes back as a failureReason so
     * the caller can send without the attachment and record why. One file ->
     * attached directly; multiple -> zipped together, same convention
     * AttachmentOrchestrationService already uses across providers.
     */
    public ExportResult export(Object caseIdRaw) {
        if (dataSource == null) {
            return failure("case-tbl DB is not configured (hs-notification.case-tbl-datasource.url is unset)");
        }

        String caseIdStr = caseIdRaw == null ? null : caseIdRaw.toString().trim();
        if (caseIdStr == null || caseIdStr.isBlank()) {
            return failure("case_id missing from context");
        }

        long caseId;
        try {
            caseId = Long.parseLong(caseIdStr);
        } catch (NumberFormatException e) {
            return failure("case_id has an unexpected format — refusing to query");
        }

        List<Doc> docs = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_DOCS_SQL)) {
            ps.setLong(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    docs.add(new Doc(rs.getString("name"), rs.getString("sub_type"), rs.getBytes("document")));
                }
            }
        } catch (Exception e) {
            log.warn("case-evidence export failed for case_id={}: {}", caseId, e.getMessage());
            return failure("case-tbl DB query failed: " + e.getMessage());
        }

        if (docs.isEmpty()) {
            return failure("no evidence files found for case_id=" + caseId);
        }

        if (docs.size() == 1) {
            Doc only = docs.get(0);
            return new ExportResult(only.bytes(), filenameFor(only), contentTypeFor(only.subType()), null);
        }

        try {
            return new ExportResult(zip(docs), "case_evidence_" + caseId + ".zip", "application/zip", null);
        } catch (IOException e) {
            log.warn("case-evidence zip failed for case_id={}: {}", caseId, e.getMessage());
            return failure("failed to bundle evidence files: " + e.getMessage());
        }
    }

    private static ExportResult failure(String reason) {
        return new ExportResult(null, null, null, reason);
    }

    private static String filenameFor(Doc doc) {
        String safeName = (doc.name() == null || doc.name().isBlank() ? "evidence" : doc.name())
                .replace("/", "_").replace("\\", "_");
        String ext = doc.subType() == null ? "" : "." + doc.subType().toLowerCase(Locale.ROOT);
        return safeName.endsWith(ext) ? safeName : safeName + ext;
    }

    private static String contentTypeFor(String subType) {
        if (subType == null) return "application/octet-stream";
        return switch (subType.toLowerCase(Locale.ROOT)) {
            case "pdf" -> "application/pdf";
            case "csv" -> "text/csv";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private static byte[] zip(List<Doc> docs) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
            for (Doc doc : docs) {
                zos.putNextEntry(new ZipEntry(filenameFor(doc)));
                zos.write(doc.bytes());
                zos.closeEntry();
            }
        }
        return buffer.toByteArray();
    }
}
