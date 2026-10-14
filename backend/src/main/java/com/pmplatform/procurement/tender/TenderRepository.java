package com.pmplatform.procurement.tender;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenderRepository extends JpaRepository<Tender, UUID> {

    Optional<Tender> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Locks the tender row so bid receipt and opening cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tender t where t.tenantId = :tenantId and t.id = :id")
    Optional<Tender> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    List<Tender> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable page);

    List<Tender> findByTenantIdAndRequisitionIdOrderByCreatedAtDesc(UUID tenantId, UUID requisitionId);

    boolean existsByTenantIdAndRequisitionIdAndStatusNotIn(UUID tenantId, UUID requisitionId,
            java.util.Collection<TenderStatus> statuses);
}
