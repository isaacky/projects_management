package com.pmplatform.procurement.requisition;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.ForbiddenActionException;
import com.pmplatform.procurement.plan.PlanCoverage;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A request to buy something for a project, and its approval workflow.
 *
 * <pre>
 *   DRAFT ──submit──▶ SUBMITTED ──last step approves──▶ APPROVED
 *     ▲                  │  ├──any step rejects──▶ REJECTED
 *     │                  │  └──any step returns──▶ RETURNED ──edit, resubmit──▶ SUBMITTED (new round)
 *     └── cancel ◀───────┴── (requester withdraws) ──▶ CANCELLED
 * </pre>
 *
 * The approval chain is copied in when the requisition is submitted, so later
 * changes to the procurement settings never affect a requisition in approval.
 */
@Entity
@Table(schema = "procurement", name = "requisitions")
public class Requisition extends TenantEntity {

    /** A line as entered by the requester. */
    public record LineInput(String description, BigDecimal quantity, String unit, long unitPriceMinor) {
    }

    /** One approver step to copy in at submission. */
    public record ChainStep(int stepOrder, String approverRole) {
    }

    /** What an approval decision led to, so the caller can tell the right people. */
    public record Decision(RequisitionApproval step, Optional<RequisitionApproval> nextStep,
            RequisitionStatus resultingStatus) {
    }

    @Column(nullable = false, updatable = false)
    private String number;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String title;

    private String justification;

    private String department;

    @Column(name = "needed_by")
    private LocalDate neededBy;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "estimated_total_minor", nullable = false)
    private long estimatedTotalMinor;

    @Column(name = "proposed_method_code")
    private String proposedMethodCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequisitionStatus status;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "current_round", nullable = false)
    private int currentRound;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** The annual procurement plan line this requisition draws on. */
    @Column(name = "plan_line_id")
    private UUID planLineId;

    /** Why the purchase is not in the plan (when no line is named). */
    @Column(name = "unplanned_reason")
    private String unplannedReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status")
    private PlanCoverage planStatus;

    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<RequisitionLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("round, stepOrder")
    private List<RequisitionApproval> approvals = new ArrayList<>();

    protected Requisition() {
    }

    public Requisition(UUID tenantId, String number, UUID requestedBy, Instant now) {
        super(tenantId, now);
        this.number = number;
        this.requestedBy = requestedBy;
        this.status = RequisitionStatus.DRAFT;
        this.createdAt = now;
    }

    // ------------------------------------------------------------------ editing

    public boolean isEditable() {
        return status == RequisitionStatus.DRAFT || status == RequisitionStatus.RETURNED;
    }

    public void edit(UUID userId, UUID projectId, String title, String justification, String department,
            LocalDate neededBy, String currency, String proposedMethodCode, List<LineInput> newLines,
            Instant now) {
        requireRequester(userId, "edit");
        if (!isEditable()) {
            throw new BusinessRuleException("Requisition " + number + " is " + status
                    + " and can no longer be changed");
        }
        this.projectId = projectId;
        this.title = title;
        this.justification = justification;
        this.department = department;
        this.neededBy = neededBy;
        this.currency = currency;
        this.proposedMethodCode = proposedMethodCode;
        lines.clear();
        int n = 1;
        long total = 0;
        for (LineInput in : newLines) {
            RequisitionLine line = new RequisitionLine(this, n++, in.description(), in.quantity(), in.unit(),
                    in.unitPriceMinor());
            lines.add(line);
            total = Math.addExact(total, line.getTotalMinor());
        }
        this.estimatedTotalMinor = total;
        touch(now);
    }

    // ------------------------------------------------------------------ workflow

    /** Starts a new approval round with the given chain; returns the first step. */
    public RequisitionApproval submit(UUID userId, List<ChainStep> chain, Instant now) {
        requireRequester(userId, "submit");
        if (!isEditable()) {
            throw new BusinessRuleException("Only a draft or returned requisition can be submitted");
        }
        if (lines.isEmpty() || estimatedTotalMinor <= 0) {
            throw new BusinessRuleException("Add at least one line with a value before submitting");
        }
        if (chain.isEmpty()) {
            throw new BusinessRuleException("No approver is configured for a requisition of this value");
        }
        currentRound++;
        List<ChainStep> ordered = chain.stream().sorted(Comparator.comparingInt(ChainStep::stepOrder)).toList();
        RequisitionApproval first = null;
        for (ChainStep step : ordered) {
            RequisitionApproval a = new RequisitionApproval(this, currentRound, step.stepOrder(),
                    step.approverRole(), first == null ? ApprovalStepStatus.PENDING : ApprovalStepStatus.WAITING);
            approvals.add(a);
            if (first == null) {
                first = a;
            }
        }
        status = RequisitionStatus.SUBMITTED;
        submittedAt = now;
        decidedAt = null;
        touch(now);
        return first;
    }

    public Decision approve(UUID userId, Set<String> userRoles, String comment, Instant now) {
        RequisitionApproval step = requireMyPendingStep(userId, userRoles, "approve");
        step.decide(ApprovalStepStatus.APPROVED, userId, blankToNull(comment), now);
        Optional<RequisitionApproval> next = currentRoundSteps().stream()
                .filter(a -> a.getStatus() == ApprovalStepStatus.WAITING)
                .min(Comparator.comparingInt(RequisitionApproval::getStepOrder));
        if (next.isPresent()) {
            next.get().setStatus(ApprovalStepStatus.PENDING);
        } else {
            status = RequisitionStatus.APPROVED;
            decidedAt = now;
        }
        touch(now);
        return new Decision(step, next, status);
    }

    public Decision reject(UUID userId, Set<String> userRoles, String comment, Instant now) {
        return close(userId, userRoles, comment, now, ApprovalStepStatus.REJECTED, RequisitionStatus.REJECTED,
                "reject");
    }

    public Decision returnForChanges(UUID userId, Set<String> userRoles, String comment, Instant now) {
        return close(userId, userRoles, comment, now, ApprovalStepStatus.RETURNED, RequisitionStatus.RETURNED,
                "return");
    }

    /** The requester withdraws the requisition. Returns the step that was waiting on someone, if any. */
    public Optional<RequisitionApproval> cancel(UUID userId, Instant now) {
        requireRequester(userId, "cancel");
        if (status != RequisitionStatus.DRAFT && status != RequisitionStatus.RETURNED
                && status != RequisitionStatus.SUBMITTED) {
            throw new BusinessRuleException("A requisition that is " + status + " cannot be cancelled");
        }
        Optional<RequisitionApproval> pending = pendingStep();
        cancelOpenSteps();
        status = RequisitionStatus.CANCELLED;
        decidedAt = now;
        touch(now);
        return pending;
    }

    private Decision close(UUID userId, Set<String> userRoles, String comment, Instant now,
            ApprovalStepStatus stepOutcome, RequisitionStatus outcome, String verb) {
        if (comment == null || comment.isBlank()) {
            throw new BusinessRuleException("Give a reason when you " + verb + " a requisition");
        }
        RequisitionApproval step = requireMyPendingStep(userId, userRoles, verb);
        step.decide(stepOutcome, userId, comment.trim(), now);
        cancelOpenSteps();
        status = outcome;
        decidedAt = now;
        touch(now);
        return new Decision(step, Optional.empty(), status);
    }

    private RequisitionApproval requireMyPendingStep(UUID userId, Set<String> userRoles, String verb) {
        if (status != RequisitionStatus.SUBMITTED) {
            throw new BusinessRuleException("Requisition " + number + " is not awaiting approval");
        }
        RequisitionApproval step = pendingStep()
                .orElseThrow(() -> new BusinessRuleException("No approval step is pending"));
        if (!userRoles.contains(step.getApproverRole())) {
            throw new ForbiddenActionException("This step needs the " + step.getApproverRole() + " role");
        }
        if (userId.equals(requestedBy)) {
            throw new ForbiddenActionException("You cannot " + verb + " your own requisition");
        }
        return step;
    }

    private void cancelOpenSteps() {
        currentRoundSteps().stream()
                .filter(a -> a.getStatus() == ApprovalStepStatus.WAITING
                        || a.getStatus() == ApprovalStepStatus.PENDING)
                .forEach(a -> a.setStatus(ApprovalStepStatus.CANCELLED));
    }

    private void requireRequester(UUID userId, String verb) {
        if (!requestedBy.equals(userId)) {
            throw new ForbiddenActionException("Only the requester can " + verb + " this requisition");
        }
    }

    // ------------------------------------------------------------------ queries

    public Optional<RequisitionApproval> pendingStep() {
        return currentRoundSteps().stream().filter(a -> a.getStatus() == ApprovalStepStatus.PENDING).findFirst();
    }

    public List<RequisitionApproval> currentRoundSteps() {
        return approvals.stream().filter(a -> a.getRound() == currentRound)
                .sorted(Comparator.comparingInt(RequisitionApproval::getStepOrder)).toList();
    }

    public boolean canBeDecidedBy(UUID userId, Set<String> userRoles) {
        return status == RequisitionStatus.SUBMITTED && !userId.equals(requestedBy)
                && pendingStep().map(s -> userRoles.contains(s.getApproverRole())).orElse(false);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Names the plan line, or gives the reason the purchase is unplanned (a named line clears the reason). */
    public void linkPlan(UUID lineId, String reason, Instant now) {
        if (!isEditable()) {
            throw new BusinessRuleException("Requisition " + number + " is " + status
                    + " and can no longer be changed");
        }
        this.planLineId = lineId;
        this.unplannedReason = lineId != null || reason == null || reason.isBlank() ? null : reason.trim();
        this.planStatus = null; // decided again on the next submission
        touch(now);
    }

    /** Set when the requisition is submitted. */
    public void planChecked(PlanCoverage coverage) {
        this.planStatus = coverage;
    }

    public UUID getPlanLineId() { return planLineId; }
    public String getUnplannedReason() { return unplannedReason; }
    public PlanCoverage getPlanStatus() { return planStatus; }
    public String getNumber() { return number; }
    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getJustification() { return justification; }
    public String getDepartment() { return department; }
    public LocalDate getNeededBy() { return neededBy; }
    public String getCurrency() { return currency; }
    public long getEstimatedTotalMinor() { return estimatedTotalMinor; }
    public String getProposedMethodCode() { return proposedMethodCode; }
    public RequisitionStatus getStatus() { return status; }
    public UUID getRequestedBy() { return requestedBy; }
    public int getCurrentRound() { return currentRound; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<RequisitionLine> getLines() { return List.copyOf(lines); }
    public List<RequisitionApproval> getApprovals() { return List.copyOf(approvals); }
}
