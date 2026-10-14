package com.pmplatform.procurement.requisition;

import com.pmplatform.platform.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** One approval step of one submission round. */
@Entity
@Table(schema = "procurement", name = "requisition_approvals")
public class RequisitionApproval {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requisition_id", nullable = false, updatable = false)
    private Requisition requisition;

    @Column(nullable = false, updatable = false)
    private int round;

    @Column(name = "step_order", nullable = false, updatable = false)
    private int stepOrder;

    @Column(name = "approver_role", nullable = false, updatable = false)
    private String approverRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStepStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    private String comment;

    @Version
    private Long version;

    protected RequisitionApproval() {
    }

    RequisitionApproval(Requisition requisition, int round, int stepOrder, String approverRole,
            ApprovalStepStatus status) {
        this.id = UuidV7.generate();
        this.requisition = requisition;
        this.round = round;
        this.stepOrder = stepOrder;
        this.approverRole = approverRole;
        this.status = status;
    }

    void decide(ApprovalStepStatus outcome, UUID userId, String comment, Instant now) {
        this.status = outcome;
        this.decidedBy = userId;
        this.comment = comment;
        this.decidedAt = now;
    }

    void setStatus(ApprovalStepStatus status) {
        this.status = status;
    }

    public UUID getId() { return id; }
    public int getRound() { return round; }
    public int getStepOrder() { return stepOrder; }
    public String getApproverRole() { return approverRole; }
    public ApprovalStepStatus getStatus() { return status; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getComment() { return comment; }
}
