package com.pmplatform.benefits;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementRepository extends JpaRepository<Measurement, UUID> {

    List<Measurement> findByTenantIdAndBenefitIdInOrderByMeasuredOnAsc(UUID tenantId, Collection<UUID> benefitIds);

    boolean existsByBenefitIdAndMeasuredOn(UUID benefitId, java.time.LocalDate measuredOn);
}
