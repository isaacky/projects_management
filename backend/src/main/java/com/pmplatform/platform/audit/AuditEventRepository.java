package com.pmplatform.platform.audit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Optional<AuditEvent> findFirstByTenantIdOrderBySeqDesc(UUID tenantId);

    List<AuditEvent> findByTenantIdAndEntityIdOrderBySeqAsc(UUID tenantId, UUID entityId);
}
