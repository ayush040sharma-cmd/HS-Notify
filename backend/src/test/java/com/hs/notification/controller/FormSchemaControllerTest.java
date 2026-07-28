package com.hs.notification.controller;

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
 * Integration coverage for the Form Metadata Engine (Phase 2) — form_schema
 * CRUD and the GET /api/v1/actions/{code}/schema read path the notification
 * wizard is meant to call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class FormSchemaControllerTest {

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

    // --- migration-seeded example schema ---

    @Test
    void fraudAlertActionHasASeededSchemaWithExpectedFields() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/actions/FRAUD_ALERT/schema", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> schema = (Map<String, Object>) response.getBody().get("schema");
        assertThat(schema).isNotNull();
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");
        assertThat(fields).extracting(f -> f.get("fieldKey"))
                .contains("to_address", "severity", "custom_message", "include_pr_records");

        Map<String, Object> priorityField = fields.stream()
                .filter(f -> "priority".equals(f.get("fieldKey"))).findFirst().orElseThrow();
        assertThat((List) priorityField.get("options")).hasSize(4);
    }

    @Test
    void actionWithoutASchemaReturnsNullGracefully() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/actions/CASE_SUMMARY/schema", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("schema")).isNull();
        assertThat(response.getBody().get("actionCode")).isEqualTo("CASE_SUMMARY");
    }

    @Test
    void unknownActionCodeReturns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/actions/NOT_REAL/schema", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- form-schema CRUD ---

    @Test
    void createWithNestedFieldsValidationsAndOptionsRoundTrips() {
        Map<String, Object> body = Map.of(
                "name", "Test Schema",
                "description", "created by integration test",
                "fields", List.of(
                        Map.of(
                                "fieldKey", "subject", "label", "Subject", "fieldType", "TEXTBOX",
                                "required", true, "displayOrder", 1,
                                "validations", List.of(Map.of("validationType", "MAX_LENGTH", "validationValue", "100", "errorMessage", "too long"))
                        ),
                        Map.of(
                                "fieldKey", "priority", "label", "Priority", "fieldType", "DROPDOWN",
                                "displayOrder", 2,
                                "options", List.of(
                                        Map.of("optionValue", "LOW", "optionLabel", "Low", "displayOrder", 1),
                                        Map.of("optionValue", "HIGH", "optionLabel", "High", "displayOrder", 2))
                        )
                )
        );

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/form-schemas", HttpMethod.POST, new HttpEntity<>(body, authHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number id = (Number) createResponse.getBody().get("id");
        List<Map<String, Object>> fields = (List<Map<String, Object>>) createResponse.getBody().get("fields");
        assertThat(fields).hasSize(2);

        // delete cleanup (not linked to any action)
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/v1/form-schemas/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void invalidFieldTypeIsRejected() {
        Map<String, Object> body = Map.of(
                "name", "Bad Schema",
                "fields", List.of(Map.of("fieldKey", "x", "label", "X", "fieldType", "NOT_A_TYPE"))
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/form-schemas", HttpMethod.POST, new HttpEntity<>(body, authHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deletingASchemaLinkedToAnActionIsBlocked() {
        // schema id 1 is the migration-seeded Fraud Alert Form, linked to FRAUD_ALERT
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/form-schemas/1", HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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
