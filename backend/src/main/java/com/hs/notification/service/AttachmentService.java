package com.hs.notification.service;

import com.hs.notification.model.AttachmentRule;
import com.hs.notification.model.NotificationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final RestClient restClient;
    private final String reportServiceBaseUrl;

    public AttachmentService(
            RestClient.Builder restClientBuilder,
            @Value("${hs-notification.report-service.base-url:}") String reportServiceBaseUrl,
            @Value("${hs-notification.report-service.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${hs-notification.report-service.read-timeout-ms:30000}") long readTimeoutMs) {

        this.reportServiceBaseUrl = reportServiceBaseUrl;
        this.restClient = restClientBuilder
                .requestInterceptors(interceptors -> {})
                .build();
    }

    public void attachIfConfigured(NotificationJob job, AttachmentRule rule, Map<String, Object> context) {
        if (rule == null || !rule.isActive() || "NONE".equals(rule.getAttachmentSource())) {
            job.setAttachmentStatus("NOT_APPLICABLE");
            return;
        }

        job.setAttachmentStatus("PENDING");
        try {
            String path = switch (rule.getAttachmentSource()) {
                case "GENERATED_PDF"  -> generateSimplePdf(job, context);
                case "REPORT_SERVICE" -> callReportService(rule, job, context);
                case "STATIC_FILE"    -> rule.getReportIdentifier();
                default               -> null;
            };

            if (path != null) {
                job.setAttachmentPath(path);
                job.setAttachmentStatus("GENERATED");
            } else {
                handleFailure(job, rule, "Attachment source returned no content");
            }
        } catch (AttachmentException e) {
            log.warn("Attachment generation failed [{}]: {}", e.kind, e.getMessage());
            handleFailure(job, rule, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected attachment failure for job {}", job.getJobId(), e);
            handleFailure(job, rule, e.getMessage());
        }
    }

    /**
     * Calls the external HS report service.
     * Failure modes map to distinct exception kinds so the on_generation_failure
     * policy can be exercised independently in tests for each case.
     */
    String callReportService(AttachmentRule rule, NotificationJob job, Map<String, Object> context) {
        byte[] pdfBytes = fetchReportBytes(rule.getReportIdentifier(), job.getTenant().getTenantId(), job.getSourceReference());

        try {
            File tmp = Files.createTempFile("hs-report-", ".pdf").toFile();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(pdfBytes);
            }
            log.info("Report downloaded ({} bytes) for job {} → {}", pdfBytes.length, job.getJobId(), tmp.getAbsolutePath());
            return tmp.getAbsolutePath();
        } catch (Exception e) {
            throw new AttachmentException("UNKNOWN", "Failed to write downloaded report to disk: " + e.getMessage());
        }
    }

    /**
     * Public so AttachmentProvider implementations (Phase 4) can reuse this
     * same HTTP call/error-mapping without going through the AttachmentRule-
     * shaped legacy entry point above. isReportServiceConfigured() lets a
     * provider report availability without triggering an HTTP call.
     */
    public byte[] fetchReportBytes(String reportIdentifier, Long tenantId, String sourceReference) {
        if (reportServiceBaseUrl == null || reportServiceBaseUrl.isBlank()) {
            throw new AttachmentException("CONFIGURATION",
                    "Report service base URL is not configured (set hs-notification.report-service.base-url)");
        }

        String base = reportServiceBaseUrl.endsWith("/")
                ? reportServiceBaseUrl.substring(0, reportServiceBaseUrl.length() - 1)
                : reportServiceBaseUrl;
        String url = base + "/reports/" + reportIdentifier;

        try {
            byte[] bytes = restClient.get()
                    .uri(url, uriBuilder -> uriBuilder
                            .queryParam("tenantId", tenantId)
                            .queryParam("sourceRef", sourceReference)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        throw new AttachmentException("CLIENT_ERROR",
                                "Report service returned " + resp.getStatusCode() + " for " + reportIdentifier
                                + " — check reportIdentifier config");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new AttachmentException("SERVER_ERROR",
                                "Report service returned " + resp.getStatusCode() + " — transient error, job may be retried");
                    })
                    .body(byte[].class);

            if (bytes == null || bytes.length == 0) {
                throw new AttachmentException("EMPTY_RESPONSE", "Report service returned empty body");
            }
            return bytes;

        } catch (AttachmentException e) {
            throw e;
        } catch (ResourceAccessException e) {
            // Network-level failure: timeout, refused, DNS
            throw new AttachmentException("TIMEOUT", "Report service unreachable: " + e.getMessage());
        } catch (Exception e) {
            throw new AttachmentException("UNKNOWN", "Report service call failed: " + e.getMessage());
        }
    }

    public boolean isReportServiceConfigured() {
        return reportServiceBaseUrl != null && !reportServiceBaseUrl.isBlank();
    }

    private void handleFailure(NotificationJob job, AttachmentRule rule, String reason) {
        job.setAttachmentStatus("FAILED");
        job.setLastError("Attachment failure: " + reason);
        switch (rule.getOnGenerationFailure()) {
            case "FAIL_JOB"  -> job.setStatus("FAILED");
            case "HOLD_JOB"  -> job.setStatus("PENDING");
            default          -> { /* SEND_WITHOUT_ATTACHMENT — continue with send */ }
        }
    }

    private String generateSimplePdf(NotificationJob job, Map<String, Object> context) throws Exception {
        File tmp = Files.createTempFile("hs-notify-", ".pdf").toFile();
        String html = "<html><body><h2>Notification Summary</h2><pre>" + context + "</pre></body></html>";
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
        }
        return tmp.getAbsolutePath();
    }

    /** Typed exception so callers can distinguish failure kinds in tests. */
    static class AttachmentException extends RuntimeException {
        final String kind;
        AttachmentException(String kind, String message) {
            super(message);
            this.kind = kind;
        }
    }
}
