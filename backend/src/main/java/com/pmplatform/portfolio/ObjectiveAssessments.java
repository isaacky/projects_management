package com.pmplatform.portfolio;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * What the post-implementation reviews found for a project's objectives. Implemented by the benefits
 * context, which runs the reviews; the portfolio uses it to show each objective's latest outcome and to
 * stop an objective that a review has assessed from being deleted. Implementations must not depend on
 * {@link ProjectObjectiveService}.
 */
public interface ObjectiveAssessments {

    /** The outcome from the most recent completed review, per objective id. */
    Map<UUID, Latest> latest(UUID tenantId, UUID projectId);

    /** Whether any review, draft or completed, has assessed the objective. */
    boolean isAssessed(UUID objectiveId);

    record Latest(ObjectiveOutcome outcome, String evidence, String reviewTitle, LocalDate conductedOn) {
    }
}
