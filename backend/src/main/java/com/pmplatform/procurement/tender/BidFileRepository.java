package com.pmplatform.procurement.tender;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidFileRepository extends JpaRepository<BidFile, UUID> {

    List<BidFile> findByBidIdInOrderByUploadedAtAsc(Collection<UUID> bidIds);

    List<BidFile> findByBidIdOrderByUploadedAtAsc(UUID bidId);

    Optional<BidFile> findByBidIdAndId(UUID bidId, UUID id);
}
