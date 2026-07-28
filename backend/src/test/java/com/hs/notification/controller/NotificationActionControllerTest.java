package com.hs.notification.controller;

import com.hs.notification.repository.NotificationActionRepository;
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
 * Integration coverage for the Notification Action Registry (Phase 1 of the
 * metadata-driven platform rollout) — the database-backed CRUD API that
 * replaces the hardcoded scenario set NotificationService used to enforce.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class NotificationActionControllerTest {

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

    // --- migration seed data ---

    @Test
    void fiveLegacyScenariosAreSeededAndEnabled() {
        List<String> codes = actionRepository.findAllByOrderByDisplayOrderAsc()
                .stream().map(a -> a.getCode()).toList();

        assertThat(codes).contains(
                "FRAUD_ALERT", "ZERO_TOLERANCE", "VENDOR_EMAIL", "CASE_SUMMARY", "PR_CLOSE");
        assertThat(actionRepository.findByCode("PR_CLOSE").orElseThrow().isEnabled()).isTrue();
    }

    // --- CRUD lifecycle ---

    @Test
    void createGetUpdateDeleteLifecycle() {
        HttpHeaders headers = authHeaders();

        Map<String, Object> createBody = Map.of(
                "code", "TEST_LIFECYCLE_ACTION",
                "displayName", "Test Lifecycle Action",
                "description", "Created by an integration test",
                "enabled", true,
                "approvalRequired", false,
                "defaultChannel", "EMAIL",
                "displayOrder", 999
        );

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/actions", HttpMethod.POST, new HttpEntity<>(createBody, headers), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number id = (Number) createResponse.getBody().get("id");
        assertThat(id).isNotNull();

        // duplicate code is rejected
        ResponseEntity<Map> dupeResponse = restTemplate.exchange(
                "/api/v1/actions", HttpMethod.POST, new HttpEntity<>(createBody, headers), Map.class);
        assertThat(dupeResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // get by id
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/v1/actions/" + id, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("displayName")).isEqualTo("Test Lifecycle Action");

        // list includes it
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/v1/actions", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(listResponse.getBody()).anyMatch(a -> "TEST_LIFECYCLE_ACTION".equals(((Map) a).get("code")));

        // update
        Map<String, Object> updateBody = Map.of(
                "code", "TEST_LIFECYCLE_ACTION",
                "displayName", "Renamed Action",
                "enabled", false,
                "displayOrder", 999
        );
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
                "/api/v1/actions/" + id, HttpMethod.PUT, new HttpEntity<>(updateBody, headers), Map.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("displayName")).isEqualTo("Renamed Action");
        assertThat(updateResponse.getBody().get("enabled")).isEqualTo(false);

        // delete
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/v1/actions/" + id, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> afterDelete = restTemplate.exchange(
                "/api/v1/actions/" + id, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getUnknownIdReturns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/actions/999999999", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-HS-API-Key", "test-api-key");
        headers.set("Authorization", "Bearer " + login());
        return headers;
    }

    private String login() {
        Map<String, String> creds = Map.of("username", "admin", "password", "admin123");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/auth/login", creds, Map.class);
        return (String) response.getBody().get("token");
    }
}
