package com.pmplatform.procurement.evaluation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationBidResultRepository extends JpaRepository<EvaluationBidResult, UUID> {

    List<EvaluationBidResult> findByEvaluationId(UUID evaluationId);
}
