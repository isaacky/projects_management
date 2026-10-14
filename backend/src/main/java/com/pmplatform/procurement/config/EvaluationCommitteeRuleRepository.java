package com.pmplatform.procurement.config;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationCommitteeRuleRepository extends JpaRepository<EvaluationCommitteeRule, UUID> {

    List<EvaluationCommitteeRule> findByTenantId(UUID tenantId);

    Optional<EvaluationCommitteeRule> findByTenantIdAndMethodCode(UUID tenantId, String methodCode);

    Optional<EvaluationCommitteeRule> findByTenantIdAndMethodCodeIsNull(UUID tenantId);
}
