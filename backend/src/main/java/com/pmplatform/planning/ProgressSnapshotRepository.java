package com.pmplatform.planning;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressSnapshotRepository extends JpaRepository<ProgressSnapshot, UUID> {

    List<ProgressSnapshot> findByTenantIdAndProjectIdOrderByAsOfAsc(UUID tenantId, UUID projectId);

    Optional<ProgressSnapshot> findByProjectIdAndAsOf(UUID projectId, LocalDate asOf);
}
