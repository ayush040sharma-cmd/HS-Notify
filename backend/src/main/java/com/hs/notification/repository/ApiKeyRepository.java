package com.hs.notification.repository;

import com.hs.notification.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByKeyPrefixAndRevokedFalse(String keyPrefix);

    boolean existsByTenant_TenantIdAndRevokedFalse(Long tenantId);

    List<ApiKey> findByTenant_TenantIdOrderByCreatedAtDesc(Long tenantId);
}
