package com.hs.notification.service;

import com.hs.notification.model.AppUser;
import com.hs.notification.model.NotificationRule;
import com.hs.notification.repository.AppUserRepository;
import com.hs.notification.repository.NotificationRuleRepository;
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
 * Integration coverage for the CURRENT_USER dynamic recipient type —
 * NotificationService.resolveCurrentUserRecipient's four-tier resolution
 * order (acting_user_email -> acting_username lookup -> case_owner_email ->
 * fallback_recipient_group -> hard failure).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class CurrentUserRecipientTest {

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
    NotificationRuleRepository ruleRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Test
    void actingUserEmailInPayloadIsUsedDirectly() {
        setPrCloseRuleRecipientMode("CURRENT_USER", null);
        try {
            Map<String, Object> body = Map.of(
                    "action", "PR_CLOSE_RULE",
                    "payload", Map.of("case_id", "1", "acting_user_email", "analyst1@subex.com")
            );
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
            assertThat(job.get("toAddresses")).isEqualTo(List.of("analyst1@subex.com"));
        } finally {
            setPrCloseRuleRecipientMode("STATIC_GROUP", null);
        }
    }

    @Test
    void actingUsernameIsLookedUpAgainstAppUserEmail() {
        AppUser user = new AppUser();
        user.setUsername("current-user-test-analyst");
        user.setEmail("looked-up@subex.com");
        user.setRole("ANALYST");
        appUserRepository.save(user);

        setPrCloseRuleRecipientMode("CURRENT_USER", null);
        try {
            Map<String, Object> body = Map.of(
                    "action", "PR_CLOSE_RULE",
                    "payload", Map.of("case_id", "1", "acting_username", "current-user-test-analyst")
            );
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
            assertThat(job.get("toAddresses")).isEqualTo(List.of("looked-up@subex.com"));
        } finally {
            setPrCloseRuleRecipientMode("STATIC_GROUP", null);
        }
    }

    @Test
    void caseOwnerEmailFallbackIsUsedWhenNoActingUser() {
        setPrCloseRuleRecipientMode("CURRENT_USER", null);
        try {
            Map<String, Object> body = Map.of(
                    "action", "PR_CLOSE_RULE",
                    "payload", Map.of("case_id", "1", "case_owner_email", "case-owner@subex.com")
            );
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> job = (Map<String, Object>) response.getBody().get("job");
            assertThat(job.get("toAddresses")).isEqualTo(List.of("case-owner@subex.com"));
        } finally {
            setPrCloseRuleRecipientMode("STATIC_GROUP", null);
        }
    }

    @Test
    void noResolvableIdentityAndNoFallbackFailsCleanly() {
        setPrCloseRuleRecipientMode("CURRENT_USER", null);
        try {
            Map<String, Object> body = Map.of(
                    "action", "PR_CLOSE_RULE",
                    "payload", Map.of("case_id", "1")
            );
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/notify", HttpMethod.POST, new HttpEntity<>(body, apiKeyHeaders()), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            setPrCloseRuleRecipientMode("STATIC_GROUP", null);
        }
    }

    private void setPrCloseRuleRecipientMode(String mode, Long fallbackGroupId) {
        NotificationRule rule = ruleRepository.findAll().stream()
                .filter(r -> "PR_CLOSE_RULE".equals(r.getRuleCode())).findFirst().orElseThrow();
        rule.setRecipientMode(mode);
        ruleRepository.save(rule);
    }

    private HttpHeaders apiKeyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-HS-API-Key", "test-api-key");
        return headers;
    }
}
