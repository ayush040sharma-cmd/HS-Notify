package com.hs.notification;

import com.hs.notification.model.NotificationRule;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.NotificationRuleRepository;
import com.hs.notification.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class SmokeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    NotificationRuleRepository ruleRepository;

    @Test
    void contextLoads() {
        // just checks that the full Spring context boots against a real Postgres
    }

    @Test
    void zainTenantExistsAndIsActive() {
        Tenant zain = tenantRepository.findByTenantCode("ZAIN").orElseThrow(
                () -> new AssertionError("ZAIN tenant not seeded"));
        assertThat(zain.isActive()).isTrue();
    }

    @Test
    void prCloseRuleIsActiveAndActive() {
        Tenant zain = tenantRepository.findByTenantCode("ZAIN").orElseThrow();
        List<NotificationRule> rules = ruleRepository.findByTenant_TenantId(zain.getTenantId());

        NotificationRule prClose = rules.stream()
                .filter(r -> "PR_CLOSE_RULE".equals(r.getRuleCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PR_CLOSE_RULE not seeded"));

        assertThat(prClose.getStatus()).isEqualTo("ACTIVE");
        assertThat(prClose.isActive()).isTrue();
    }

    @Test
    void caseEscalateRuleIsPendingReview() {
        Tenant zain = tenantRepository.findByTenantCode("ZAIN").orElseThrow();
        List<NotificationRule> rules = ruleRepository.findByTenant_TenantId(zain.getTenantId());

        NotificationRule caseEscalate = rules.stream()
                .filter(r -> "CASE_ESCALATE_RULE".equals(r.getRuleCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CASE_ESCALATE_RULE not seeded"));

        assertThat(caseEscalate.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(caseEscalate.isActive()).isFalse();
    }
}
