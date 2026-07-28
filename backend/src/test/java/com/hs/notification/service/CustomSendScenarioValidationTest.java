package com.hs.notification.service;

import com.hs.notification.model.NotificationAction;
import com.hs.notification.repository.NotificationActionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
 * Backward-compatibility regression test: /send-custom's scenario validation
 * used to check a hardcoded Set<String>; it now looks up the
 * notification_action registry (see NotificationService.submitCustomNotification).
 * These tests pin the exact same externally-visible behavior — unknown
 * scenario rejected with 400, known+enabled scenario accepted — after that
 * refactor, and additionally cover the new "disabled action" case the old
 * hardcoded set had no way to express.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class CustomSendScenarioValidationTest {

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
    NotificationActionRepository actionRepository;

    @Test
    void unknownScenarioIsRejectedWith400() {
        Map<String, Object> body = Map.of(
                "scenario", "NOT_A_REAL_SCENARIO",
                "toAddresses", List.of("ops@example.com"),
                "subject", "test"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notifications/send-custom",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, apiKeyHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void seededLegacyScenarioIsStillAccepted() {
        Map<String, Object> body = Map.of(
                "scenario", "CASE_SUMMARY",
                "toAddresses", List.of("ops@example.com"),
                "subject", "test",
                "comment", "regression test send"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notifications/send-custom",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, apiKeyHeaders()),
                Map.class);

        // Job is created and accepted (200) even though the actual SMTP send
        // will fail in this test environment — that's a separate concern from
        // scenario validation, which is what this test targets.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("job")).isNotNull();
    }

    @Test
    void disablingAnActionBlocksFutureSendsButNotThePreviouslyHardcodedOnes() {
        NotificationAction action = actionRepository.findByCode("VENDOR_EMAIL").orElseThrow();
        action.setEnabled(false);
        actionRepository.save(action);

        try {
            Map<String, Object> body = Map.of(
                    "scenario", "VENDOR_EMAIL",
                    "toAddresses", List.of("ops@example.com"),
                    "subject", "test"
            );
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/notifications/send-custom",
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(body, apiKeyHeaders()),
                    Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            action.setEnabled(true);
            actionRepository.save(action);
        }
    }

    private HttpHeaders apiKeyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-HS-API-Key", "test-api-key");
        return headers;
    }
}
