package com.pmplatform.closeout;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByTenantIdAndProjectIdOrderByCreatedAtDesc(UUID tenantId, UUID projectId);

    List<Lesson> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable page);

    Optional<Lesson> findByTenantIdAndId(UUID tenantId, UUID id);
}
