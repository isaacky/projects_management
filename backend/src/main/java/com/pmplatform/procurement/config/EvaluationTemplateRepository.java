package com.pmplatform.procurement.config;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationTemplateRepository extends JpaRepository<EvaluationTemplate, UUID> {

    List<EvaluationTemplate> findByTenantIdOrderByCodeAsc(UUID tenantId);

    Optional<EvaluationTemplate> findByTenantIdAndCode(UUID tenantId, String code);
}
