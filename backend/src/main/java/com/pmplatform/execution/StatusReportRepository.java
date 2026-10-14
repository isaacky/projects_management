package com.pmplatform.execution;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusReportRepository extends JpaRepository<StatusReport, UUID> {

    List<StatusReport> findByTenantIdAndProjectIdOrderByPeriodEndDesc(UUID tenantId, UUID projectId);

    List<StatusReport> findByTenantIdAndStatusNot(UUID tenantId, ReportStatus status);

    Optional<StatusReport> findByTenantIdAndProjectIdAndId(UUID tenantId, UUID projectId, UUID id);

    boolean existsByProjectIdAndPeriodEnd(UUID projectId, LocalDate periodEnd);
}
