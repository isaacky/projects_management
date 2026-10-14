package com.pmplatform.contracts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractDocumentRepository extends JpaRepository<ContractDocument, UUID> {

    List<ContractDocument> findByContractIdOrderByUploadedAtAsc(UUID contractId);

    Optional<ContractDocument> findByContractIdAndId(UUID contractId, UUID id);
}
