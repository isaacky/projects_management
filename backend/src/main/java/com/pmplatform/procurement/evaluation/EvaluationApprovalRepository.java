package com.pmplatform.procurement.evaluation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationApprovalRepository extends JpaRepository<EvaluationApproval, UUID> {

    List<EvaluationApproval> findByEvaluationIdOrderByReportRoundAscStepOrderAsc(UUID evaluationId);
}
