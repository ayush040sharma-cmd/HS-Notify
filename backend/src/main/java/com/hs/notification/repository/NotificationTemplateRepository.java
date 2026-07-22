package com.hs.notification.repository;

import com.hs.notification.model.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    List<NotificationTemplate> findByTenant_TenantId(Long tenantId);

    Optional<NotificationTemplate> findByTenant_TenantIdAndTemplateCodeAndStatus(
            Long tenantId, String templateCode, String status);

    List<NotificationTemplate> findByTenant_TenantIdAndTemplateCodeOrderByVersionDesc(
            Long tenantId, String templateCode);

    List<NotificationTemplate> findByStatus(String status);

    List<NotificationTemplate> findByTenant_TenantIdAndStatus(Long tenantId, String status);
}
