package com.pmplatform.procurement.evaluation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationScoreRepository extends JpaRepository<EvaluationScore, UUID> {

    List<EvaluationScore> findByEvaluationId(UUID evaluationId);

    long countByEvaluationIdAndCriterionIdIn(UUID evaluationId, List<UUID> criterionIds);
}
