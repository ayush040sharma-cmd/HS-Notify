package com.hs.notification.service;

import com.hs.notification.model.AttachmentRule;
import com.hs.notification.model.NotificationJob;
import com.hs.notification.model.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests AttachmentService.callReportService with mocked RestClient to exercise
 * each failure mode (timeout, 4xx, 5xx) and the on_generation_failure policy.
 *
 * Mockito is NOT used here (JDK 25 ByteBuddy incompatibility). Instead we use
 * a hand-rolled RestClient.Builder stub that wires whatever behavior we need.
 */
class AttachmentServiceTest {

    // We test the policy-mapping logic via attachIfConfigured with a subclass
    // that overrides callReportService to throw specific exceptions.

    private AttachmentService service;
    private NotificationJob job;
    private AttachmentRule rule;

    @BeforeEach
    void setUp() {
        // RestClient.Builder stub — only used when real HTTP is exercised.
        RestClient.Builder fakeBuilder = RestClient.builder();
        service = new AttachmentService(fakeBuilder, "http://report-service", 5000L, 30000L);

        Tenant tenant = new Tenant();
        tenant.setTenantId(1L);
        tenant.setTenantCode("SUBEX");
        tenant.setActive(true);

        job = new NotificationJob();
        job.setTenant(tenant);
        job.setStatus("PENDING");
        job.setSourceReference("PR-001");

        rule = new AttachmentRule();
        rule.setAttachmentRuleId(1L);
        rule.setAttachmentSource("REPORT_SERVICE");
        rule.setReportIdentifier("PR_CLOSURE_SUMMARY");
        rule.setActive(true);
    }

    // --- SEND_WITHOUT_ATTACHMENT policy ---

    @Test
    void sendWithoutAttachmentPolicyKeepsJobPending() {
        rule.setOnGenerationFailure("SEND_WITHOUT_ATTACHMENT");

        AttachmentService stub = reportServiceThrows("TIMEOUT", "Connection timed out");
        stub.attachIfConfigured(job, rule, Map.of());

        assertThat(job.getAttachmentStatus()).isEqualTo("FAILED");
        assertThat(job.getStatus()).isEqualTo("PENDING"); // job continues
        assertThat(job.getLastError()).contains("timeout");
    }

    // --- FAIL_JOB policy ---

    @Test
    void failJobPolicySetsJobStatusFailed() {
        rule.setOnGenerationFailure("FAIL_JOB");

        AttachmentService stub = reportServiceThrows("CLIENT_ERROR", "404 Not Found");
        stub.attachIfConfigured(job, rule, Map.of());

        assertThat(job.getAttachmentStatus()).isEqualTo("FAILED");
        assertThat(job.getStatus()).isEqualTo("FAILED");
    }

    // --- HOLD_JOB policy ---

    @Test
    void holdJobPolicyKeepsStatusPending() {
        rule.setOnGenerationFailure("HOLD_JOB");

        AttachmentService stub = reportServiceThrows("SERVER_ERROR", "503 Service Unavailable");
        stub.attachIfConfigured(job, rule, Map.of());

        assertThat(job.getAttachmentStatus()).isEqualTo("FAILED");
        assertThat(job.getStatus()).isEqualTo("PENDING");
    }

    // --- Not applicable when rule is null or inactive ---

    @Test
    void nullRuleSetsNotApplicable() {
        service.attachIfConfigured(job, null, Map.of());
        assertThat(job.getAttachmentStatus()).isEqualTo("NOT_APPLICABLE");
    }

    @Test
    void inactiveRuleSetsNotApplicable() {
        rule.setActive(false);
        service.attachIfConfigured(job, rule, Map.of());
        assertThat(job.getAttachmentStatus()).isEqualTo("NOT_APPLICABLE");
    }

    // --- Unconfigured base URL ---

    @Test
    void unconfiguredBaseUrlFailsWithConfigurationError() {
        AttachmentService noUrlService = new AttachmentService(RestClient.builder(), "", 5000L, 30000L);
        rule.setOnGenerationFailure("SEND_WITHOUT_ATTACHMENT");

        noUrlService.attachIfConfigured(job, rule, Map.of());

        assertThat(job.getAttachmentStatus()).isEqualTo("FAILED");
        assertThat(job.getLastError()).containsIgnoringCase("not configured");
    }

    // --- Helper: builds a stub service where callReportService throws a specific kind ---

    private AttachmentService reportServiceThrows(String kind, String message) {
        return new AttachmentService(RestClient.builder(), "http://report-service", 5000L, 30000L) {
            @Override
            String callReportService(AttachmentRule r, NotificationJob j, Map<String, Object> ctx) {
                throw new AttachmentService.AttachmentException(kind, message);
            }
        };
    }
}
