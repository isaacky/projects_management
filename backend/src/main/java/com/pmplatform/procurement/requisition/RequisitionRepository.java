package com.pmplatform.procurement.requisition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequisitionRepository extends JpaRepository<Requisition, UUID> {

    Optional<Requisition> findByTenantIdAndId(UUID tenantId, UUID id);

    List<Requisition> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable page);

    List<Requisition> findByTenantIdAndRequestedByOrderByCreatedAtDesc(UUID tenantId, UUID requestedBy,
            Pageable page);

    List<Requisition> findByTenantIdAndProjectIdOrderByCreatedAtDesc(UUID tenantId, UUID projectId,
            Pageable page);

    /** Requisitions whose current pending step needs one of the given roles. */
    @Query("select distinct r from Requisition r join r.approvals a "
            + "where r.tenantId = :tenantId and r.status = com.pmplatform.procurement.requisition.RequisitionStatus.SUBMITTED "
            + "and a.round = r.currentRound "
            + "and a.status = com.pmplatform.procurement.requisition.ApprovalStepStatus.PENDING "
            + "and a.approverRole in :roles and r.requestedBy <> :userId "
            + "order by r.submittedAt")
    List<Requisition> findAwaiting(@Param("tenantId") UUID tenantId, @Param("roles") Collection<String> roles,
            @Param("userId") UUID userId, Pageable page);
}
