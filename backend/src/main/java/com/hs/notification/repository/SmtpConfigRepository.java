package com.hs.notification.repository;

import com.hs.notification.model.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, Long> {
    Optional<SmtpConfig> findByTenant_TenantIdAndActiveTrue(Long tenantId);
}
