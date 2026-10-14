package com.pmplatform.execution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskRepository extends JpaRepository<Risk, UUID> {

    List<Risk> findByTenantIdAndProjectIdOrderByNumberAsc(UUID tenantId, UUID projectId);

    List<Risk> findByTenantIdAndStatus(UUID tenantId, RiskStatus status);

    Optional<Risk> findByTenantIdAndProjectIdAndId(UUID tenantId, UUID projectId, UUID id);
}
