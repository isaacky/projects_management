package com.pmplatform.contracts;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariationApprovalRepository extends JpaRepository<VariationApproval, UUID> {

    List<VariationApproval> findByVariationIdInOrderByStepOrderAsc(Collection<UUID> variationIds);
}
