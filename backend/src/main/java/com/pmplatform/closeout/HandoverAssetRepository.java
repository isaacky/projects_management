package com.pmplatform.closeout;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverAssetRepository extends JpaRepository<HandoverAsset, UUID> {

    List<HandoverAsset> findByTenantIdAndProjectIdOrderBySortOrderAsc(UUID tenantId, UUID projectId);
}
