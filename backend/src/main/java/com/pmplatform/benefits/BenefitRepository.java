package com.pmplatform.benefits;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BenefitRepository extends JpaRepository<Benefit, UUID> {

    List<Benefit> findByTenantIdAndProjectIdOrderByNumberAsc(UUID tenantId, UUID projectId);

    List<Benefit> findByTenantIdOrderByProjectIdAscNumberAsc(UUID tenantId);

    List<Benefit> findByTenantIdAndStatusIn(UUID tenantId, Collection<BenefitStatus> statuses);


    Optional<Benefit> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Serialises changes to one benefit (readings, edits, closing). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Benefit b where b.tenantId = :tenantId and b.id = :id")
    Optional<Benefit> lock(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
