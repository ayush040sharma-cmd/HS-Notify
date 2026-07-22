package com.hs.notification.service;

import com.hs.notification.model.RateLimitBucket;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class RateLimitService {

    private static final int DEFAULT_MAX_PER_MINUTE = 60;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public boolean tryAcquire(Long tenantId, Long ruleId) {
        OffsetDateTime windowStart = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        RateLimitBucket bucket = entityManager.createQuery(
                        "select b from RateLimitBucket b where b.tenant.tenantId = :tenantId " +
                                "and (b.rule.ruleId = :ruleId or (:ruleId is null and b.rule is null)) " +
                                "and b.windowStart = :windowStart", RateLimitBucket.class)
                .setParameter("tenantId", tenantId)
                .setParameter("ruleId", ruleId)
                .setParameter("windowStart", windowStart)
                .getResultStream().findFirst().orElse(null);

        if (bucket == null) {
            entityManager.createNativeQuery(
                            "insert into rate_limit_bucket (tenant_id, rule_id, window_start, send_count) " +
                                    "values (:tenantId, :ruleId, :windowStart, 0) on conflict do nothing")
                    .setParameter("tenantId", tenantId)
                    .setParameter("ruleId", ruleId)
                    .setParameter("windowStart", windowStart)
                    .executeUpdate();
        }

        int updated = entityManager.createNativeQuery(
                        "update rate_limit_bucket set send_count = send_count + 1 " +
                                "where tenant_id = :tenantId and (rule_id = :ruleId or (:ruleId is null and rule_id is null)) " +
                                "and window_start = :windowStart and send_count < :max")
                .setParameter("tenantId", tenantId)
                .setParameter("ruleId", ruleId)
                .setParameter("windowStart", windowStart)
                .setParameter("max", DEFAULT_MAX_PER_MINUTE)
                .executeUpdate();

        return updated > 0;
    }
}
