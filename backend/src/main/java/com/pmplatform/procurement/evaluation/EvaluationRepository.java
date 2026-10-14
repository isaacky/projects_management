package com.pmplatform.procurement.evaluation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    Optional<Evaluation> findByTenantIdAndTenderId(UUID tenantId, UUID tenderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Evaluation e where e.tenantId = :tenantId and e.tenderId = :tenderId")
    Optional<Evaluation> lockByTenantIdAndTenderId(@Param("tenantId") UUID tenantId, @Param("tenderId") UUID tenderId);

    List<Evaluation> findByTenantIdAndStatusIn(UUID tenantId, List<EvaluationStatus> statuses);
}
