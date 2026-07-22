package com.hs.notification.repository;

import com.hs.notification.model.NotificationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, Long> {

    Optional<NotificationJob> findByTenant_TenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);

    Page<NotificationJob> findByTenant_TenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    Page<NotificationJob> findByTenant_TenantIdAndStatusOrderByCreatedAtDesc(
            Long tenantId, String status, Pageable pageable);

    @Query("select j from NotificationJob j where j.status = 'RETRYING' and j.nextRetryAt <= :now")
    List<NotificationJob> findDueForRetry(@Param("now") OffsetDateTime now);

    long countByTenant_TenantIdAndStatusAndCreatedAtAfter(Long tenantId, String status, OffsetDateTime since);

    long countByTenant_TenantIdAndCreatedAtAfter(Long tenantId, OffsetDateTime since);

    List<NotificationJob> findByTenant_TenantIdAndCreatedAtAfter(Long tenantId, OffsetDateTime since);

    long countByTenant_TenantIdAndStatus(Long tenantId, String status);

    Optional<NotificationJob> findFirstByTenant_TenantIdAndStatusOrderByCreatedAtAsc(Long tenantId, String status);

    List<NotificationJob> findByTenant_TenantIdAndStatusAndSentAtAfter(Long tenantId, String status, OffsetDateTime since);
}
