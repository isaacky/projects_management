package com.pmplatform.benefits.api;

import com.pmplatform.benefits.BenefitCategory;
import com.pmplatform.benefits.BenefitRating;
import com.pmplatform.benefits.BenefitStatus;
import com.pmplatform.benefits.Direction;
import com.pmplatform.benefits.Frequency;
import com.pmplatform.benefits.ObjectivesMet;
import com.pmplatform.benefits.ReviewStatus;
import com.pmplatform.portfolio.ObjectiveOutcome;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for benefits realisation. */
public final class BenefitDtos {

    private BenefitDtos() {
    }

    // ---------------------------------------------------------------- requests

    public record BenefitRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull BenefitCategory category,
            @Size(max = 500) String objective,
            @NotNull UUID ownerId,
            @NotBlank @Size(max = 200) String measure,
            @NotBlank @Size(max = 32) String unit,
            @NotNull @Digits(integer = 14, fraction = 4) BigDecimal baselineValue,
            @NotNull LocalDate baselineDate,
            @NotNull @Digits(integer = 14, fraction = 4) BigDecimal targetValue,
            @NotNull LocalDate targetDate,
            @NotNull Frequency frequency,
            @PositiveOrZero Long annualValueMinor,
            Long version) {
    }

    public record MeasurementRequest(@NotNull LocalDate measuredOn,
            @NotNull @Digits(integer = 14, fraction = 4) BigDecimal value, @Size(max = 200) String source,
            @Size(max = 1000) String note) {
    }

    public record CloseRequest(@NotNull BenefitStatus outcome, @NotBlank @Size(max = 2000) String note) {
    }

    public record ReviewRequest(@NotBlank @Size(max = 200) String title, @NotNull LocalDate dueDate,
            @NotNull UUID leadId, Long version) {
    }

    public record StandardReviewsRequest(@NotNull UUID leadId) {
    }

    public record AssessmentInput(@NotNull UUID benefitId, @NotNull BenefitRating rating,
            @Size(max = 2000) String comment) {
    }

    public record ObjectiveAssessmentInput(@NotNull UUID objectiveId, @NotNull ObjectiveOutcome outcome,
            @Size(max = 2000) String evidence) {
    }

    /**
     * The review's findings. When the project has objectives, {@code objectiveAssessments} records each one's
     * outcome and the overall {@code objectivesMet} follows from them; it is only the reviewer's own call when
     * no objective's outcome is decided yet.
     */
    public record FindingsRequest(LocalDate conductedOn, ObjectivesMet objectivesMet,
            @Size(max = 4000) String summary, @Size(max = 4000) String recommendations,
            @Size(max = 200) List<@Valid AssessmentInput> assessments,
            @Size(max = 50) List<@Valid ObjectiveAssessmentInput> objectiveAssessments) {
    }

    public record NoteRequest(@Size(max = 2000) String note) {
    }

    // ---------------------------------------------------------------- responses

    public record ProjectRef(UUID id, String code, String name) {
    }

    public record MeasurementView(UUID id, LocalDate measuredOn, BigDecimal value, BigDecimal progressPct,
            String source, String note, Ref recordedBy, boolean canDelete) {
    }

    public record BenefitView(UUID id, ProjectRef project, int number, String title, String description,
            BenefitCategory category, String objective, Ref owner, String measure, String unit, Direction direction,
            BigDecimal baselineValue, LocalDate baselineDate, BigDecimal targetValue, LocalDate targetDate,
            Frequency frequency, Long annualValueMinor, BenefitStatus status, String closedNote, Ref closedBy,
            Instant closedAt, BigDecimal latestValue, LocalDate latestOn, BigDecimal progressPct,
            BigDecimal expectedPct, BenefitRating rating, LocalDate nextDue, boolean overdue, long realisedMinor,
            List<MeasurementView> measurements, boolean canEdit, boolean canMeasure, boolean canClose,
            boolean canReopen, long version) {
    }

    public record AssessmentView(UUID benefitId, int benefitNumber, String benefitTitle, String unit,
            BigDecimal baselineValue, BigDecimal targetValue, BigDecimal actualValue, LocalDate measuredOn, BigDecimal progressPct,
            BenefitRating rating, String comment) {
    }

    public record ObjectiveAssessmentView(UUID objectiveId, int objectiveNumber, String statement,
            ObjectiveOutcome outcome, String evidence) {
    }

    public record ReviewView(UUID id, ProjectRef project, String title, LocalDate dueDate, Ref lead,
            ReviewStatus status, LocalDate conductedOn, ObjectivesMet objectivesMet, String summary,
            String recommendations, String cancelNote, Ref completedBy, Instant completedAt, boolean overdue,
            List<AssessmentView> assessments, List<ObjectiveAssessmentView> objectiveAssessments, boolean canEdit,
            boolean canComplete, boolean canCancel, long version) {
    }

    /** An active project objective, for the review form. */
    public record ObjectiveRef(UUID id, int number, String statement, String successMeasure) {
    }

    public record Summary(int benefits, int active, int achieved, int onTrack, int atRisk, int offTrack,
            int notMeasured, int overdueMeasurements, long annualValueMinor, long realisedMinor, int reviewsDue) {
    }

    public record Permissions(boolean canAdd, boolean canPlanReviews) {
    }

    public record ProjectBenefitsView(UUID projectId, LocalDate handoverDate, Summary summary,
            List<BenefitView> benefits, List<ReviewView> reviews, List<ObjectiveRef> objectives,
            Permissions permissions) {
    }

    public record PortfolioView(Summary summary, List<BenefitView> benefits, List<ReviewView> upcomingReviews) {
    }
}
