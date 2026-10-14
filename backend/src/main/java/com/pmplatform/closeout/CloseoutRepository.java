package com.pmplatform.closeout;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CloseoutRepository extends JpaRepository<Closeout, UUID> {

    Optional<Closeout> findByTenantIdAndProjectId(UUID tenantId, UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Closeout c where c.tenantId = :tenantId and c.projectId = :projectId")
    Optional<Closeout> lock(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);
}
