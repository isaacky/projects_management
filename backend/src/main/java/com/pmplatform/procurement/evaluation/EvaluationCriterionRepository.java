package com.pmplatform.procurement.evaluation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationCriterionRepository extends JpaRepository<EvaluationCriterion, UUID> {

    List<EvaluationCriterion> findByEvaluationIdOrderByStageAscSortOrderAsc(UUID evaluationId);
}
