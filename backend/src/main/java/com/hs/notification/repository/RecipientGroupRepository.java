package com.hs.notification.repository;

import com.hs.notification.model.RecipientGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecipientGroupRepository extends JpaRepository<RecipientGroup, Long> {
    List<RecipientGroup> findByTenant_TenantId(Long tenantId);

    Optional<RecipientGroup> findByTenant_TenantIdAndGroupCode(Long tenantId, String groupCode);
}
