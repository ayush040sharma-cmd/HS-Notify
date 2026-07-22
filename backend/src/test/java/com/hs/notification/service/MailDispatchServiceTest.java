package com.hs.notification.service;

import com.hs.notification.model.NotificationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MailDispatchServiceTest {

    MailDispatchService service;

    @BeforeEach
    void setUp() {
        // computeBackoff uses no injected dependencies; empty sender list + nulls are safe here.
        service = new MailDispatchService(List.of(), null, null, null);
    }

    // --- null rule fallback: 60 * attemptCount ---

    @Test
    void nullRuleBackoffIsLinear() {
        assertThat(service.computeBackoff(null, 1)).isEqualTo(60);
        assertThat(service.computeBackoff(null, 2)).isEqualTo(120);
        assertThat(service.computeBackoff(null, 3)).isEqualTo(180);
    }

    // --- exponential: base 60s, multiplier 2.0 ---
    // formula: (int)(base * multiplier ^ (attempt - 1))

    @Test
    void firstAttemptReturnsBase() {
        // 60 * 2^0 = 60
        assertThat(service.computeBackoff(rule(60, 2.0), 1)).isEqualTo(60);
    }

    @Test
    void secondAttemptDoublesBase() {
        // 60 * 2^1 = 120
        assertThat(service.computeBackoff(rule(60, 2.0), 2)).isEqualTo(120);
    }

    @Test
    void thirdAttemptQuadruplesBase() {
        // 60 * 2^2 = 240
        assertThat(service.computeBackoff(rule(60, 2.0), 3)).isEqualTo(240);
    }

    // --- different base/multiplier ---

    @Test
    void backoffWith120BaseAnd1_5Multiplier() {
        // 120 * 1.5^1 = 180
        assertThat(service.computeBackoff(rule(120, 1.5), 2)).isEqualTo(180);
    }

    @Test
    void backoffIsExponentialNotLinear() {
        NotificationRule r = rule(30, 3.0);
        int a1 = service.computeBackoff(r, 1); // 30 * 3^0 = 30
        int a2 = service.computeBackoff(r, 2); // 30 * 3^1 = 90
        int a3 = service.computeBackoff(r, 3); // 30 * 3^2 = 270

        assertThat(a1).isEqualTo(30);
        assertThat(a2).isEqualTo(90);
        assertThat(a3).isEqualTo(270);
        assertThat(a2 - a1).isNotEqualTo(a3 - a2); // growth is multiplicative
    }

    private NotificationRule rule(int baseSeconds, double multiplier) {
        NotificationRule r = new NotificationRule();
        r.setRetryBackoffSeconds(baseSeconds);
        r.setRetryBackoffMultiplier(multiplier);
        return r;
    }
}
