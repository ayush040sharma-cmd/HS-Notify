package com.hs.notification.repository;

import com.hs.notification.model.NotificationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificationAuditLogRepository extends JpaRepository<NotificationAuditLog, Long> {

    Page<NotificationAuditLog> findByTenant_TenantIdOrderByOccurredAtDesc(Long tenantId, Pageable pageable);

    Page<NotificationAuditLog> findByJob_JobIdOrderByOccurredAtDesc(Long jobId, Pageable pageable);

    List<NotificationAuditLog> findByTenant_TenantIdAndEventTypeAndOccurredAtAfter(
            Long tenantId, String eventType, OffsetDateTime since);

    boolean existsByRule_RuleId(Long ruleId);
}
