package com.pmplatform.planning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    List<Activity> findByTenantIdAndProjectId(UUID tenantId, UUID projectId);
}
