package com.hs.notification.repository;

import com.hs.notification.model.EscalationChain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EscalationChainRepository extends JpaRepository<EscalationChain, Long> {
    List<EscalationChain> findByTenant_TenantId(Long tenantId);

    Optional<EscalationChain> findByTenant_TenantIdAndChainCode(Long tenantId, String chainCode);
}
