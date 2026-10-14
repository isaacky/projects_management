package com.pmplatform.procurement.plan;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcurementPlanRepository extends JpaRepository<ProcurementPlan, UUID> {

    List<ProcurementPlan> findByTenantIdOrderByFyStartDesc(UUID tenantId);

    Optional<ProcurementPlan> findByTenantIdAndId(UUID tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProcurementPlan p where p.tenantId = :tenantId and p.id = :id")
    Optional<ProcurementPlan> lock(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
