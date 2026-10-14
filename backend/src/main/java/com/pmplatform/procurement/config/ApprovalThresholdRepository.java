package com.pmplatform.procurement.config;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalThresholdRepository extends JpaRepository<ApprovalThreshold, UUID> {

    List<ApprovalThreshold> findByTenantIdOrderByStageAscMethodCodeAscMinAmountMinorAscStepOrderAsc(
            UUID tenantId);

    List<ApprovalThreshold> findByTenantIdAndStage(UUID tenantId, ApprovalStage stage);

    Optional<ApprovalThreshold> findByTenantIdAndId(UUID tenantId, UUID id);
}
