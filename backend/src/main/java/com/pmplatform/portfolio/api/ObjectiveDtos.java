package com.pmplatform.portfolio.api;

import com.pmplatform.portfolio.ObjectiveOutcome;
import com.pmplatform.portfolio.ObjectiveStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for project objectives. */
public final class ObjectiveDtos {

    private ObjectiveDtos() {
    }

    /** An objective listed when the project is created; a blank row left on the form is skipped. */
    public record ObjectiveInput(@Size(max = 500) String statement, @Size(max = 500) String successMeasure) {
    }

    /** Adds or rewords an objective; after initiation the PMO must give a reason. */
    public record ObjectiveRequest(@NotBlank @Size(max = 500) String statement,
            @Size(max = 500) String successMeasure, @Size(max = 2000) String reason, Long version) {
    }

    public record WithdrawRequest(@NotBlank @Size(max = 2000) String reason) {
    }

    public record ObjectiveView(UUID id, int number, String statement, String successMeasure, ObjectiveStatus status,
            String withdrawnReason, ObjectiveOutcome latestOutcome, String latestEvidence, String latestReview,
            LocalDate latestReviewOn, boolean canEdit, boolean canWithdraw, boolean canReinstate, boolean canDelete,
            long version) {
    }

    /**
     * A project's objectives. {@code reasonRequired} is true once the project has passed gate G0: from then on
     * only the PMO changes the objectives, and says why.
     */
    public record ObjectivesView(UUID projectId, List<ObjectiveView> objectives, boolean canAdd,
            boolean reasonRequired) {
    }
}
