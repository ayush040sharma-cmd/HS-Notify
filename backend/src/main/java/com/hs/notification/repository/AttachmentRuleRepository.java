package com.hs.notification.repository;

import com.hs.notification.model.AttachmentRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRuleRepository extends JpaRepository<AttachmentRule, Long> {
    List<AttachmentRule> findByTenant_TenantId(Long tenantId);
}
