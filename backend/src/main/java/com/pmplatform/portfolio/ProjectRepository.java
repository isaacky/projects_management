package com.pmplatform.portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every method takes the tenant id: there is no unscoped query in this repository. */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByTenantIdOrderByCodeAsc(UUID tenantId);

    List<Project> findByTenantIdAndPhaseOrderByCodeAsc(UUID tenantId, ProjectPhase phase);

    Optional<Project> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<Project> findByTenantIdAndIdIn(UUID tenantId, java.util.Collection<UUID> ids);
}
