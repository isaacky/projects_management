package com.pmplatform.execution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

    List<Issue> findByTenantIdAndProjectIdOrderByNumberAsc(UUID tenantId, UUID projectId);

    List<Issue> findByTenantId(UUID tenantId);

    Optional<Issue> findByTenantIdAndProjectIdAndId(UUID tenantId, UUID projectId, UUID id);
}
