package com.pmplatform.benefits;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByTenantIdAndProjectIdOrderByDueDateAsc(UUID tenantId, UUID projectId);

    List<Review> findByTenantIdOrderByDueDateAsc(UUID tenantId);

    List<Review> findByTenantIdAndStatusAndRemindedFalse(UUID tenantId, ReviewStatus status);

    Optional<Review> findByTenantIdAndId(UUID tenantId, UUID id);

}
