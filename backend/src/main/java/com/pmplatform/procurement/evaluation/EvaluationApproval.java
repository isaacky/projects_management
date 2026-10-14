package com.pmplatform.procurement.evaluation;

import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** One step after the report is signed: the professional opinion, or an award approval. */
@Entity
@Table(schema = "procurement", name = "evaluation_approvals")
public class EvaluationApproval {

    @Id
    private UUID id;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "round", nullable = false, updatable = false)
    private int reportRound;

    @Column(name = "step_order", nullable = false, updatable = false)
    private int stepOrder;

    @Column(name = "approver_role", nullable = false, updatable = false)
    private String approverRole;

    @Column(nullable = false, updatable = false)
    private boolean opinion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    private String note;

    @Version
    private Long version;

    protected EvaluationApproval() {
    }

    public EvaluationApproval(UUID evaluationId, int round, int stepOrder, String approverRole, boolean opinion) {
        this.id = UuidV7.generate();
        this.evaluationId = evaluationId;
        this.reportRound = round;
        this.stepOrder = stepOrder;
        this.approverRole = approverRole;
        this.opinion = opinion;
        this.status = ApprovalStatus.PENDING;
    }

    public void decide(ApprovalStatus outcome, UUID by, String note, Instant now) {
        if (status != ApprovalStatus.PENDING) {
            throw new BusinessRuleException("This step has already been decided");
        }
        if (outcome == ApprovalStatus.PENDING || outcome == ApprovalStatus.SKIPPED) {
            throw new IllegalArgumentException("Not a decision: " + outcome);
        }
        boolean needsNote = opinion || outcome != ApprovalStatus.APPROVED;
        if (needsNote && (note == null || note.isBlank())) {
            throw new BusinessRuleException(opinion && outcome == ApprovalStatus.APPROVED
                    ? "Write the professional opinion"
                    : "Give the reason");
        }
        this.status = outcome;
        this.decidedBy = by;
        this.decidedAt = now;
        this.note = note == null || note.isBlank() ? null : note.trim();
    }

    public void skip() {
        if (status == ApprovalStatus.PENDING) {
            status = ApprovalStatus.SKIPPED;
        }
    }

    public UUID getId() { return id; }
    public UUID getEvaluationId() { return evaluationId; }
    public int getRound() { return reportRound; }
    public int getStepOrder() { return stepOrder; }
    public String getApproverRole() { return approverRole; }
    public boolean isOpinion() { return opinion; }
    public ApprovalStatus getStatus() { return status; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getNote() { return note; }
}
