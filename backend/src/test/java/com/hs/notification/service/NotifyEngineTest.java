package com.hs.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for Phase 3: POST /api/v1/notify, and the backward-
 * compatibility guarantee that send-direct/send-custom/send-by-rule keep
 * their exact existing external contracts while now delegating internally
 * to NotificationService.notify().
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class NotifyEngineTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void notifyWithAdHocActionCreatesAJob() {
        Map<String, Object> body = Map.of(
                "action", "CASE_SUMMARY",
                "recipients", Map.of("to", List.of("ops@example.com"), "cc", List.of("cc@example.com"), "bcc", List.of("bcc@example.com")),
                "subject", "notify engine test",
                "comment", "sent via /api/v1/notify"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
        assertThat(job.get("ccAddresses")).isEqualTo(List.of("cc@example.com"));
        assertThat(job.get("bccAddresses")).isEqualTo(List.of("bcc@example.com"));
    }

    @Test
    void notifyWithRuleCodeRoutesThroughTheRuleEngine() {
        Map<String, Object> body = Map.of(
                "action", "PR_CLOSE_RULE",
                "recipients", Map.of("to", List.of("override@example.com")),
                "payload", Map.of("case_id", "999")
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
        assertThat(job.get("ruleCode")).isEqualTo("PR_CLOSE_RULE");
        assertThat(job.get("toAddresses")).isEqualTo(List.of("override@example.com"));
    }

    @Test
    void notifyWithUnknownActionReturns400() {
        Map<String, Object> body = Map.of(
                "action", "NOT_A_RULE_OR_ACTION",
                "recipients", Map.of("to", List.of("ops@example.com")),
                "subject", "x"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Object Registry auto-routing (V16__notification_object_registry) ---

    @Test
    void notifyWithSourceTypeCaseAutoRoutesToEvidenceProvider() {
        Map<String, Object> body = Map.of(
                "action", "CASE_SUMMARY",
                "recipients", Map.of("to", List.of("ops@example.com")),
                "subject", "object registry auto-route test",
                "sourceType", "CASE",
                "payload", Map.of("case_id", "12345")
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
        // case-tbl-datasource isn't configured in this test environment, so EVIDENCE
        // fails gracefully — but reaching FAILED (not NOT_APPLICABLE) proves the
        // registry resolved sourceType=CASE -> EVIDENCE and actually tried.
        assertThat(job.get("attachmentStatus")).isEqualTo("FAILED");
        List<String> notices = (List<String>) response.getBody().get("notices");
        assertThat(notices).anyMatch(n -> n.contains("EVIDENCE"));
    }

    @Test
    void notifyWithSourceTypePrAutoRoutesToPrRecordsProvider() {
        Map<String, Object> body = Map.of(
                "action", "CASE_SUMMARY",
                "recipients", Map.of("to", List.of("ops@example.com")),
                "subject", "object registry auto-route test",
                "sourceType", "PR",
                "payload", Map.of("case_id", "12345", "catalog_id", "7")
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
        // usage-datasource isn't configured in this test environment either — same
        // graceful-failure proof that PR -> PR_RECORDS was actually resolved and tried.
        assertThat(job.get("attachmentStatus")).isEqualTo("FAILED");
        List<String> notices = (List<String>) response.getBody().get("notices");
        assertThat(notices).anyMatch(n -> n.contains("PR_RECORDS"));
    }

    @Test
    void notifyWithUnregisteredSourceTypeNeverAutoRoutes() {
        Map<String, Object> body = Map.of(
                "action", "CASE_SUMMARY",
                "recipients", Map.of("to", List.of("ops@example.com")),
                "subject", "object registry no-match test",
                // Real-world descriptive sourceType, not a bare registry object_type —
                // must NOT fuzzy-match "CASE" and auto-route.
                "sourceType", "CASE_ESCALATION"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
        assertThat(job.get("attachmentStatus")).isEqualTo("NOT_APPLICABLE");
    }

    // --- backward compatibility: legacy endpoints keep their existing contracts ---

    @Test
    void legacySendCustomStillWorksUnchanged() {
        Map<String, Object> body = Map.of(
                "scenario", "CASE_SUMMARY",
                "toAddresses", List.of("ops@example.com"),
                "subject", "legacy send-custom",
                "comment", "still works after Phase 3"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notifications/send-custom", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("job")).isNotNull();
        assertThat(response.getBody().get("notices")).isNotNull();
    }

    @Test
    void legacySendDirectStillWorksUnchanged() {
        Map<String, Object> body = Map.of(
                "to", List.of("ops@example.com"),
                "subject", "legacy send-direct",
                "htmlBody", "<p>still works after Phase 3</p>"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notifications/send-direct", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("jobId")).isNotNull();
    }

    @Test
    void legacySendDirectStillRejectsMissingSubjectAndTemplate() {
        Map<String, Object> body = Map.of("to", List.of("ops@example.com"));
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notifications/send-direct", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpHeaders apiKeyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-HS-API-Key", "test-api-key");
        return headers;
    }
}
