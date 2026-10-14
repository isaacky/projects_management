package com.pmplatform.procurement.config;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcurementMethodRepository extends JpaRepository<ProcurementMethod, UUID> {

    List<ProcurementMethod> findByTenantIdOrderBySortOrderAscCodeAsc(UUID tenantId);

    Optional<ProcurementMethod> findByTenantIdAndCode(UUID tenantId, String code);
}
