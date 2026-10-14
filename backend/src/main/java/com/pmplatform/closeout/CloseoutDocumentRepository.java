package com.pmplatform.closeout;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CloseoutDocumentRepository extends JpaRepository<CloseoutDocument, UUID> {

    List<CloseoutDocument> findByTenantIdAndProjectIdOrderByUploadedAtAsc(UUID tenantId, UUID projectId);
}
