package com.pmplatform.procurement.tender;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    List<Bid> findByTenantIdAndTenderIdOrderByReceivedAtAsc(UUID tenantId, UUID tenderId);

    Optional<Bid> findByTenantIdAndTenderIdAndId(UUID tenantId, UUID tenderId, UUID id);

    boolean existsByTenderIdAndSupplierIdAndStatus(UUID tenderId, UUID supplierId, BidStatus status);

    long countByTenderId(UUID tenderId);
}
