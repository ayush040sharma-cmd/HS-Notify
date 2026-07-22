package com.hs.notification.repository;

import com.hs.notification.model.NotificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {

    List<NotificationRule> findByTenant_TenantId(Long tenantId);

    Optional<NotificationRule> findByTenant_TenantIdAndRuleCode(Long tenantId, String ruleCode);

    Optional<NotificationRule> findByTenant_TenantIdAndTriggerEventAndActiveTrueAndStatus(
            Long tenantId, String triggerEvent, String status);

    List<NotificationRule> findByStatus(String status);

    List<NotificationRule> findByTenant_TenantIdAndStatus(Long tenantId, String status);
}
