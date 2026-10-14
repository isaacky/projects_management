package com.pmplatform.planning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DependencyRepository extends JpaRepository<Dependency, UUID> {

    List<Dependency> findByProjectId(UUID projectId);
}
