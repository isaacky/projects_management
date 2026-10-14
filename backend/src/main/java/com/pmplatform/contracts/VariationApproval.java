package com.pmplatform.contracts;

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

/** One step of a variation's approval chain (from the CONTRACT_VARIATION thresholds). */
@Entity
@Table(schema = "contracts", name = "variation_approvals")
public class VariationApproval {

    @Id
    private UUID id;

    @Column(name = "variation_id", nullable = false, updatable = false)
    private UUID variationId;

    @Column(name = "step_order", nullable = false, updatable = false)
    private int stepOrder;

    @Column(name = "approver_role", nullable = false, updatable = false)
    private String approverRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    private String note;

    @Version
    private Long version;

    protected VariationApproval() {
    }

    public VariationApproval(UUID variationId, int stepOrder, String approverRole) {
        this.id = UuidV7.generate();
        this.variationId = variationId;
        this.stepOrder = stepOrder;
        this.approverRole = approverRole;
        this.status = StepStatus.PENDING;
    }

    public void decide(boolean approve, UUID by, String note, Instant now) {
        if (status != StepStatus.PENDING) {
            throw new BusinessRuleException("This step has already been decided");
        }
        if (!approve && (note == null || note.isBlank())) {
            throw new BusinessRuleException("Give the reason for rejecting the variation");
        }
        status = approve ? StepStatus.APPROVED : StepStatus.REJECTED;
        decidedBy = by;
        decidedAt = now;
        this.note = note == null || note.isBlank() ? null : note.trim();
    }

    public void skip() {
        if (status == StepStatus.PENDING) {
            status = StepStatus.SKIPPED;
        }
    }

    public UUID getId() { return id; }
    public UUID getVariationId() { return variationId; }
    public int getStepOrder() { return stepOrder; }
    public String getApproverRole() { return approverRole; }
    public StepStatus getStatus() { return status; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getNote() { return note; }
}
