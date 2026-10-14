package com.pmplatform.closeout;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.portfolio.ProjectPhase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A lesson learned on a project, reviewed by the PMO. */
@Entity
@Table(schema = "closeout", name = "lessons")
public class Lesson extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LessonKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LessonCategory category;

    @Enumerated(EnumType.STRING)
    private ProjectPhase phase;

    @Column(nullable = false)
    private String title;

    @Column(name = "what_happened", nullable = false)
    private String whatHappened;

    private String recommendation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LessonStatus status;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Lesson() {
    }

    public Lesson(UUID tenantId, UUID projectId, UUID createdBy, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.status = LessonStatus.SUBMITTED;
    }

    public void write(LessonKind kind, LessonCategory category, ProjectPhase phase, String title, String whatHappened,
            String recommendation, Instant now) {
        if (status != LessonStatus.SUBMITTED) {
            throw new BusinessRuleException("The PMO has reviewed this lesson; it can no longer be changed");
        }
        if (kind == null || category == null) {
            throw new BusinessRuleException("Choose whether it went well or should improve, and the category");
        }
        this.kind = kind;
        this.category = category;
        this.phase = phase;
        this.title = Text.required(title, 200, "The title");
        this.whatHappened = Text.required(whatHappened, 4000, "What happened");
        this.recommendation = Text.optional(recommendation, 4000, "The recommendation");
        touch(now);
    }

    public void review(LessonStatus to, UUID by, String note, Instant now) {
        if (to == LessonStatus.SUBMITTED) {
            throw new BusinessRuleException("Mark the lesson adopted or noted");
        }
        if (status != LessonStatus.SUBMITTED) {
            throw new BusinessRuleException("This lesson was already " + status.name().toLowerCase());
        }
        String n = to == LessonStatus.ADOPTED ? Text.required(note, 2000, "What will change")
                : Text.optional(note, 2000, "The note");
        this.status = to;
        this.reviewedBy = by;
        this.reviewedAt = now;
        this.reviewNote = n;
        touch(now);
    }

    public UUID getProjectId() { return projectId; }
    public LessonKind getKind() { return kind; }
    public LessonCategory getCategory() { return category; }
    public ProjectPhase getPhase() { return phase; }
    public String getTitle() { return title; }
    public String getWhatHappened() { return whatHappened; }
    public String getRecommendation() { return recommendation; }
    public LessonStatus getStatus() { return status; }
    public String getReviewNote() { return reviewNote; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
