package com.pmplatform.closeout;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplateItem, UUID> {

    List<ChecklistTemplateItem> findByTenantIdOrderBySortOrderAsc(UUID tenantId);
}
