package com.pmplatform.benefits;

import com.pmplatform.portfolio.ObjectiveAssessments;
import com.pmplatform.portfolio.ObjectiveOutcome;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tells the portfolio what the completed post-implementation reviews found for each objective. */
@Component
class BenefitObjectiveAssessments implements ObjectiveAssessments {

    private final ReviewRepository reviews;
    private final ReviewObjectiveAssessmentRepository assessments;

    BenefitObjectiveAssessments(ReviewRepository reviews, ReviewObjectiveAssessmentRepository assessments) {
        this.reviews = reviews;
        this.assessments = assessments;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Latest> latest(UUID tenantId, UUID projectId) {
        // Oldest first, so a later review's finding replaces an earlier one.
        List<Review> done = reviews.findByTenantIdAndProjectIdOrderByDueDateAsc(tenantId, projectId).stream()
                .filter(r -> r.getStatus() == ReviewStatus.COMPLETED)
                .sorted(Comparator.comparing(Review::getConductedOn).thenComparing(Review::getCompletedAt))
                .toList();
        if (done.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ReviewObjectiveAssessment>> byReview = new HashMap<>();
        assessments.findByReviewIdIn(done.stream().map(Review::getId).toList())
                .forEach(a -> byReview.computeIfAbsent(a.getReviewId(), k -> new java.util.ArrayList<>()).add(a));
        Map<UUID, Latest> out = new HashMap<>();
        for (Review r : done) {
            for (ReviewObjectiveAssessment a : byReview.getOrDefault(r.getId(), List.of())) {
                // "Too early to tell" does not hide an earlier review's decided outcome.
                Latest before = out.get(a.getObjectiveId());
                if (a.getOutcome() == ObjectiveOutcome.TOO_EARLY && before != null
                        && before.outcome() != ObjectiveOutcome.TOO_EARLY) {
                    continue;
                }
                out.put(a.getObjectiveId(), new Latest(a.getOutcome(), a.getEvidence(), r.getTitle(),
                        r.getConductedOn()));
            }
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAssessed(UUID objectiveId) {
        return assessments.existsByObjectiveId(objectiveId);
    }
}
