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

    // --- Attachment Schema bundling (V13__attachment_provider_pattern) ---

    @Test
    void notifyWithIncludeAttachmentBundlesAllSchemaProviders() {
        // PR_CLOSE is linked to attachment_schema id=1 ("PR Records Bundle"),
        // whose ordered provider list is [PR_RECORDS, EXCEL_EXPORT] — proves
        // includeAttachment resolves the whole schema, not one hardcoded key.
        Map<String, Object> body = Map.of(
                "action", "PR_CLOSE",
                "recipients", Map.of("to", List.of("ops@example.com")),
                "subject", "attachment schema bundle test",
                "attachmentOptions", Map.of("includeAttachment", true),
                "payload", Map.of("case_id", "12345", "catalog_id", "7")
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
        // usage-datasource isn't configured in this test environment, so both
        // providers fail gracefully — but both notices appearing (not just one)
        // proves the schema's full ordered provider list was resolved and tried,
        // not a single hardcoded provider.
        assertThat(job.get("attachmentStatus")).isEqualTo("FAILED");
        List<String> notices = (List<String>) response.getBody().get("notices");
        assertThat(notices).anyMatch(n -> n.contains("PR_RECORDS"));
        assertThat(notices).anyMatch(n -> n.contains("EXCEL_EXPORT"));
    }

    // --- Recipient resolution: group refs + bare usernames (additive to literal emails) ---

    @Test
    void notifyWithGroupReferenceExpandsToAllActiveMembersRegardlessOfStoredType() {
        // FRAUD_OPS_TEAM (V2 seed data, now under tenant SUBEX post-V11 rename) has
        // one TO member and one CC member. Placed in recipients.to, both must come
        // back — the caller's placement (to) wins over each member's own stored
        // recipient_type.
        Map<String, Object> body = Map.of(
                "action", "CASE_SUMMARY",
                "recipients", Map.of("to", List.of("group:FRAUD_OPS_TEAM")),
                "subject", "group recipient test"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
        assertThat((List<String>) job.get("toAddresses"))
                .containsExactlyInAnyOrder("fraud-ops@zain.example.com", "fraud-ops-lead@zain.example.com");
    }

    @Test
    void notifyWithMixedLiteralAndGroupRecipientsResolvesBoth() {
        Map<String, Object> body = Map.of(
                "action", "CASE_SUMMARY",
                "recipients", Map.of("to", List.of("literal@example.com", "group:FRAUD_OPS_TEAM")),
                "subject", "mixed recipient test"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
        assertThat((List<String>) job.get("toAddresses")).containsExactlyInAnyOrder(
                "literal@example.com", "fraud-ops@zain.example.com", "fraud-ops-lead@zain.example.com");
    }

    @Test
    void notifyWithUnknownGroupReferenceFailsHardInsteadOfSendingPartial() {
        Map<String, Object> body = Map.of(
                "action", "CASE_SUMMARY",
                "recipients", Map.of("to", List.of("group:NOT_A_REAL_GROUP")),
                "subject", "unknown group test"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void notifyWithUnresolvableBareUsernameFailsHardInsteadOfSendingPartial() {
        // No "@" and no "group:" prefix -> treated as a bare username and resolved
        // via UserDirectoryResolver. That directory datasource isn't configured in
        // this test environment, so resolution always misses here — proving the
        // attempt happens and fails hard rather than silently emailing the literal
        // string "not.a.real.user" as if it were an address.
        Map<String, Object> body = Map.of(
                "action", "CASE_SUMMARY",
                "recipients", Map.of("to", List.of("not.a.real.user")),
                "subject", "unresolvable username test"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Bug fix: recipient resolution must run before form validation, not after ---
    // (FRAUD_ALERT is linked to form_schema id=1, whose to_address field has an
    // EMAIL_FORMAT rule — this is the exact schema that previously rejected
    // "group:FRAUD_OPS_TEAM" and bare usernames as invalid emails.)

    @Test
    void notifyWithGroupReferenceOnFormValidatedActionResolvesInsteadOfFailingValidation() {
        Map<String, Object> body = Map.of(
                "action", "FRAUD_ALERT",
                "sourceType", "NA",
                "payload", Map.of(
                        "to_address", "group:FRAUD_OPS_TEAM",
                        "subject", "form-validated group recipient test",
                        "priority", "HIGH",
                        "severity", "HIGH")
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
        assertThat((List<String>) job.get("toAddresses"))
                .containsExactlyInAnyOrder("fraud-ops@zain.example.com", "fraud-ops-lead@zain.example.com");
    }

    @Test
    void notifyWithUnresolvableUsernameOnFormValidatedActionFailsAsRecipientErrorNotFormError() {
        Map<String, Object> body = Map.of(
                "action", "FRAUD_ALERT",
                "sourceType", "NA",
                "payload", Map.of(
                        "to_address", "not.a.real.user",
                        "subject", "form-validated unresolvable username test",
                        "priority", "HIGH",
                        "severity", "HIGH")
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Must fail via recipient resolution (BAD_REQUEST from the plain
        // IllegalArgumentException resolveRecipientTokens throws), NOT via
        // FORM_VALIDATION_ERROR — proving resolution ran before, not after,
        // form validation saw the raw "not.a.real.user" token.
        assertThat(response.getBody().get("error")).isEqualTo("BAD_REQUEST");
        assertThat((String) response.getBody().get("message")).contains("not.a.real.user");
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
