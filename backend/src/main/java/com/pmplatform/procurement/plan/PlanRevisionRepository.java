package com.pmplatform.procurement.plan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRevisionRepository extends JpaRepository<PlanRevision, UUID> {

    List<PlanRevision> findByTenantIdAndPlanIdOrderByRevisionDesc(UUID tenantId, UUID planId);
}
