package com.pmplatform.procurement.tender;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderDocumentRepository extends JpaRepository<TenderDocument, UUID> {

    List<TenderDocument> findByTenderIdOrderByUploadedAtAsc(UUID tenderId);

    Optional<TenderDocument> findByTenderIdAndId(UUID tenderId, UUID id);
}
