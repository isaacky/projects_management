package com.pmplatform.benefits;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A post-implementation review: planned with a lead and a date, then completed and frozen. */
@Entity
@Table(schema = "benefits", name = "reviews")
public class Review extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String title;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status;

    @Column(name = "conducted_on")
    private LocalDate conductedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "objectives_met")
    private ObjectivesMet objectivesMet;

    private String summary;

    private String recommendations;

    @Column(name = "cancel_note")
    private String cancelNote;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false)
    private boolean reminded;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(UUID tenantId, UUID projectId, UUID createdBy, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.status = ReviewStatus.PLANNED;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public void plan(String title, LocalDate dueDate, UUID leadId, Instant now) {
        requirePlanned();
        if (dueDate == null || leadId == null) {
            throw new BusinessRuleException("Give the review date and who leads it");
        }
        if (!dueDate.equals(this.dueDate) || !leadId.equals(this.leadId)) {
            this.reminded = false;
        }
        this.title = Text.required(title, 200, "The title");
        this.dueDate = dueDate;
        this.leadId = leadId;
        touch(now);
    }

    /** Draft findings can be saved while planned; complete() freezes them. */
    public void findings(LocalDate conductedOn, ObjectivesMet met, String summary, String recommendations,
            LocalDate today, Instant now) {
        requirePlanned();
        if (conductedOn != null && conductedOn.isAfter(today)) {
            throw new BusinessRuleException("The review date cannot be in the future");
        }
        this.conductedOn = conductedOn;
        this.objectivesMet = met;
        this.summary = Text.optional(summary, 4000, "The summary");
        this.recommendations = Text.optional(recommendations, 4000, "The recommendations");
        touch(now);
    }

    /** When the per-objective outcomes decide the overall result, it replaces the reviewer's own answer. */
    public void objectivesMetFromObjectives(ObjectivesMet derived) {
        requirePlanned();
        if (derived != null) {
            this.objectivesMet = derived;
        }
    }

    public void complete(UUID by, Instant now) {
        requirePlanned();
        if (conductedOn == null || objectivesMet == null || summary == null) {
            throw new BusinessRuleException("Give the date the review was held, whether the objectives were met and "
                    + "a summary before completing it");
        }
        this.status = ReviewStatus.COMPLETED;
        this.completedBy = by;
        this.completedAt = now;
        touch(now);
    }

    public void cancel(String note, Instant now) {
        requirePlanned();
        this.cancelNote = Text.required(note, 2000, "The reason");
        this.status = ReviewStatus.CANCELLED;
        touch(now);
    }

    public void reminded() {
        this.reminded = true;
    }

    private void requirePlanned() {
        if (status != ReviewStatus.PLANNED) {
            throw new BusinessRuleException("The review is " + status.name().toLowerCase() + " and cannot be changed");
        }
    }

    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public LocalDate getDueDate() { return dueDate; }
    public UUID getLeadId() { return leadId; }
    public ReviewStatus getStatus() { return status; }
    public LocalDate getConductedOn() { return conductedOn; }
    public ObjectivesMet getObjectivesMet() { return objectivesMet; }
    public String getSummary() { return summary; }
    public String getRecommendations() { return recommendations; }
    public String getCancelNote() { return cancelNote; }
    public UUID getCompletedBy() { return completedBy; }
    public Instant getCompletedAt() { return completedAt; }
    public boolean isReminded() { return reminded; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
