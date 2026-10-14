package com.pmplatform.procurement.evaluation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationMemberRepository extends JpaRepository<EvaluationMember, UUID> {

    List<EvaluationMember> findByEvaluationIdOrderByAddedAtAsc(UUID evaluationId);

    List<EvaluationMember> findByUserId(UUID userId);
}
