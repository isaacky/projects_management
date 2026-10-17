package com.pmplatform.benefits;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewObjectiveAssessmentRepository extends JpaRepository<ReviewObjectiveAssessment, UUID> {

    List<ReviewObjectiveAssessment> findByReviewIdIn(Collection<UUID> reviewIds);

    boolean existsByObjectiveId(UUID objectiveId);
}
