package com.pmplatform.procurement.plan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanDepartmentRepository extends JpaRepository<PlanDepartment, UUID> {

    List<PlanDepartment> findByTenantIdAndPlanIdOrderByNameAsc(UUID tenantId, UUID planId);
}
