package com.hs.notification.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

@Service
public class FeatureToggleService {

    @PersistenceContext
    private EntityManager entityManager;

    public boolean isNotificationsEnabled(Long tenantId) {
        return isEnabled(tenantId, "NOTIFICATIONS_ENABLED");
    }

    public boolean isAttachmentsEnabled(Long tenantId) {
        return isEnabled(tenantId, "ATTACHMENTS_ENABLED");
    }

    private boolean isEnabled(Long tenantId, String featureKey) {
        Long count = entityManager.createQuery(
                        "select count(f) from FeatureToggle f where f.tenant.tenantId = :tenantId " +
                                "and f.featureKey = :key and f.enabled = true", Long.class)
                .setParameter("tenantId", tenantId)
                .setParameter("key", featureKey)
                .getSingleResult();
        if (count != null && count > 0) return true;

        Long disabledCount = entityManager.createQuery(
                        "select count(f) from FeatureToggle f where f.tenant.tenantId = :tenantId " +
                                "and f.featureKey = :key and f.enabled = false", Long.class)
                .setParameter("tenantId", tenantId)
                .setParameter("key", featureKey)
                .getSingleResult();
        return disabledCount == null || disabledCount == 0;
    }
}
