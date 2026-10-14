package com.pmplatform.contracts;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariationRepository extends JpaRepository<Variation, UUID> {

    List<Variation> findByContractIdOrderByNumberAsc(UUID contractId);
}
