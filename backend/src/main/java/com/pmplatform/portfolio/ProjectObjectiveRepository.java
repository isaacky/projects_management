package com.pmplatform.portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectObjectiveRepository extends JpaRepository<ProjectObjective, UUID> {

    List<ProjectObjective> findByTenantIdAndProjectIdOrderByNumberAsc(UUID tenantId, UUID projectId);

    Optional<ProjectObjective> findByTenantIdAndId(UUID tenantId, UUID id);
}
