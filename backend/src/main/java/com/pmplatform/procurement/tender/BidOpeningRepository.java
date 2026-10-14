package com.pmplatform.procurement.tender;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidOpeningRepository extends JpaRepository<BidOpening, UUID> {

    List<BidOpening> findByTenantIdAndTenderIdOrderByOpenedAtAsc(UUID tenantId, UUID tenderId);
}
