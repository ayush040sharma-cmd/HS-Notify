package com.hs.notification.repository;

import com.hs.notification.model.WhatsAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsAppConfigRepository extends JpaRepository<WhatsAppConfig, Long> {
    Optional<WhatsAppConfig> findByTenant_TenantIdAndActiveTrue(Long tenantId);
}
