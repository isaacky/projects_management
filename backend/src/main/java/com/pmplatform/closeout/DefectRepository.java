package com.pmplatform.closeout;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefectRepository extends JpaRepository<Defect, UUID> {

    List<Defect> findByTenantIdAndProjectIdOrderByNumberAsc(UUID tenantId, UUID projectId);

    long countByTenantIdAndContractIdAndStatusIn(UUID tenantId, UUID contractId, Collection<DefectStatus> statuses);
}
