package com.pmplatform.procurement.plan;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanLineRepository extends JpaRepository<PlanLine, UUID> {

    List<PlanLine> findByTenantIdAndPlanIdOrderByLineNoAsc(UUID tenantId, UUID planId);

    List<PlanLine> findByTenantIdAndPlanIdIn(UUID tenantId, Collection<UUID> planIds);

    Optional<PlanLine> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByDepartmentId(UUID departmentId);

    /** Serialises requisitions drawing on the same line, so two cannot both take the last of its balance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from PlanLine l where l.tenantId = :tenantId and l.id = :id")
    Optional<PlanLine> lock(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
