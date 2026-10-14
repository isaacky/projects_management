package com.pmplatform.planning;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    Optional<Schedule> findByTenantIdAndProjectId(UUID tenantId, UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.tenantId = :tenantId and s.projectId = :projectId")
    Optional<Schedule> lock(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);
}
