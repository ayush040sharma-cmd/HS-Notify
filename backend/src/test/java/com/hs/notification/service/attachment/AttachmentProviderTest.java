package com.hs.notification.service.attachment;

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

/** Integration coverage for Phase 4: the attachment provider registry, its REST API, and backward compatibility of includePrRecords. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AttachmentProviderTest {

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

    @Autowired
    AttachmentProviderRegistry registry;

    @Test
    void allSevenProvidersAreRegistered() {
        List<String> keys = registry.all().stream().map(AttachmentProvider::key).toList();
        assertThat(keys).containsExactlyInAnyOrder(
                "PR_RECORDS", "CASE_PDF", "EXCEL_EXPORT",
                "DASHBOARD_SNAPSHOT", "EVIDENCE", "SUBSCRIBER_HISTORY", "CDR_SUMMARY");
    }

    @Test
    void stubProvidersReportUnavailableWithAClearReason() {
        AttachmentProvider dashboard = registry.find("DASHBOARD_SNAPSHOT").orElseThrow();
        assertThat(dashboard.isAvailable()).isFalse();
        AttachmentResult result = dashboard.generate(new AttachmentContext(null, Map.of()));
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("Phase 6");
    }

    @Test
    void providersEndpointListsEveryRegisteredProvider() {
        HttpHeaders headers = authHeaders();
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/v1/attachments/providers", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(7);
    }

    @Test
    void legacyIncludePrRecordsFlagStillWorksUnchangedWithoutUsageDbConfigured() {
        Map<String, Object> body = Map.of(
                "scenario", "CASE_SUMMARY",
                "toAddresses", List.of("ops@example.com"),
                "subject", "backward compat check",
                "flags", Map.of("includePrRecords", true)
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notifications/send-custom", HttpMethod.POST, new HttpEntity<>(body, apiKeyOnlyHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> notices = (List<String>) response.getBody().get("notices");
        assertThat(notices).anyMatch(n -> n.toString().contains("PR records CSV not attached"));
    }

    @Test
    void unknownProviderKeyInAttachmentSchemaIsRejected() {
        Map<String, Object> body = Map.of("name", "Bad Schema", "providerKeys", List.of("NOT_A_REAL_PROVIDER"));
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/attachment-schemas", HttpMethod.POST, new HttpEntity<>(body, authHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = apiKeyOnlyHeaders();
        headers.set("Authorization", "Bearer " + login());
        return headers;
    }

    private HttpHeaders apiKeyOnlyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-HS-API-Key", "test-api-key");
        return headers;
    }

    private String login() {
        Map<String, String> creds = Map.of("username", "admin", "password", "admin123");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/auth/login", creds, Map.class);
        return (String) response.getBody().get("token");
    }
}
