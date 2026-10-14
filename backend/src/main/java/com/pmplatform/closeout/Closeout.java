package com.pmplatform.closeout;

import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** A project's closeout and handover. One per project (the id is the project id). See {@link CloseoutStatus}. */
@Entity
@Table(schema = "closeout", name = "closeouts")
public class Closeout implements Persistable<UUID> {

    /** Final figures frozen when the handover is accepted. */
    public record Figures(long budgetMinor, long contractValueMinor, long certifiedMinor, long paidMinor,
            LocalDate baselineFinish, LocalDate actualFinish, BigDecimal earnedPct, LocalDate dlpEnd) {
    }

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CloseoutStatus status;

    @Column(name = "started_by", nullable = false, updatable = false)
    private UUID startedBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "receiving_unit")
    private String receivingUnit;

    @Column(name = "receiving_officer_id")
    private UUID receivingOfficerId;

    @Column(name = "handover_date")
    private LocalDate handoverDate;

    @Column(name = "handover_note")
    private String handoverNote;

    @Column(name = "handover_submitted_by")
    private UUID handoverSubmittedBy;

    @Column(name = "handover_submitted_at")
    private Instant handoverSubmittedAt;

    @Column(name = "handover_decided_by")
    private UUID handoverDecidedBy;

    @Column(name = "handover_decided_at")
    private Instant handoverDecidedAt;

    @Column(name = "handover_decision_note")
    private String handoverDecisionNote;

    @Column(name = "budget_minor")
    private Long budgetMinor;

    @Column(name = "contract_value_minor")
    private Long contractValueMinor;

    @Column(name = "certified_minor")
    private Long certifiedMinor;

    @Column(name = "paid_minor")
    private Long paidMinor;

    @Column(name = "baseline_finish")
    private LocalDate baselineFinish;

    @Column(name = "actual_finish")
    private LocalDate actualFinish;

    @Column(name = "earned_pct")
    private BigDecimal earnedPct;

    @Column(name = "dlp_end")
    private LocalDate dlpEnd;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closure_note")
    private String closureNote;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @Transient
    private boolean isNew = true;

    protected Closeout() {
    }

    public Closeout(UUID tenantId, UUID projectId, UUID startedBy, Instant now) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.status = CloseoutStatus.OPEN;
        this.startedBy = startedBy;
        this.startedAt = now;
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return status == CloseoutStatus.OPEN;
    }

    public void requireOpen(String action) {
        if (status != CloseoutStatus.OPEN) {
            throw new BusinessRuleException("The handover has been " + (status == CloseoutStatus.HANDOVER_SUBMITTED
                    ? "submitted" : "accepted") + "; " + action + " is no longer possible");
        }
    }

    public void planHandover(String unit, UUID officerId, LocalDate date, String note, Instant now) {
        requireOpen("changing the handover");
        this.receivingUnit = limited(unit, 120, "The receiving unit");
        this.receivingOfficerId = officerId;
        this.handoverDate = date;
        this.handoverNote = limited(note, 4000, "The handover note");
        this.updatedAt = now;
    }

    public void submitHandover(UUID by, Instant now) {
        requireOpen("submitting the handover");
        if (receivingUnit == null || receivingOfficerId == null || handoverDate == null) {
            throw new BusinessRuleException("Name the receiving unit, the receiving officer and the handover date");
        }
        if (by.equals(receivingOfficerId)) {
            throw new BusinessRuleException("The receiving officer cannot also hand the project over");
        }
        this.status = CloseoutStatus.HANDOVER_SUBMITTED;
        this.handoverSubmittedBy = by;
        this.handoverSubmittedAt = now;
        this.handoverDecidedBy = null;
        this.handoverDecidedAt = null;
        this.handoverDecisionNote = null;
        this.updatedAt = now;
    }

    public void acceptHandover(UUID by, String note, Figures f, Instant now) {
        requireSubmitted(by);
        this.status = CloseoutStatus.HANDED_OVER;
        decided(by, note, now);
        this.budgetMinor = f.budgetMinor();
        this.contractValueMinor = f.contractValueMinor();
        this.certifiedMinor = f.certifiedMinor();
        this.paidMinor = f.paidMinor();
        this.baselineFinish = f.baselineFinish();
        this.actualFinish = f.actualFinish();
        this.earnedPct = f.earnedPct();
        this.dlpEnd = f.dlpEnd();
    }

    public void rejectHandover(UUID by, String note, Instant now) {
        requireSubmitted(by);
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Say what must be put right before the handover can be accepted");
        }
        this.status = CloseoutStatus.OPEN;
        decided(by, note, now);
    }

    public void close(UUID by, String note, Instant now) {
        if (status != CloseoutStatus.HANDED_OVER) {
            throw new BusinessRuleException("A project is closed after its handover has been accepted");
        }
        this.status = CloseoutStatus.CLOSED;
        this.closedBy = by;
        this.closedAt = now;
        this.closureNote = limited(note, 2000, "The closure note");
        this.updatedAt = now;
    }

    public void changed(Instant now) {
        this.updatedAt = now;
    }

    public Figures frozenFigures() {
        if (budgetMinor == null) {
            return null;
        }
        return new Figures(budgetMinor, contractValueMinor, certifiedMinor, paidMinor, baselineFinish, actualFinish,
                earnedPct, dlpEnd);
    }

    private void requireSubmitted(UUID by) {
        if (status != CloseoutStatus.HANDOVER_SUBMITTED) {
            throw new BusinessRuleException("The handover has not been submitted");
        }
        if (!by.equals(receivingOfficerId)) {
            throw new com.pmplatform.platform.web.ForbiddenActionException(
                    "Only the named receiving officer can accept or reject the handover");
        }
    }

    private void decided(UUID by, String note, Instant now) {
        this.handoverDecidedBy = by;
        this.handoverDecidedAt = now;
        this.handoverDecisionNote = limited(note, 2000, "The note");
        this.updatedAt = now;
    }

    private static String limited(String s, int max, String what) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        if (t.length() > max) {
            throw new BusinessRuleException(what + " is at most " + max + " characters");
        }
        return t;
    }

    @Override
    public UUID getId() { return projectId; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getProjectId() { return projectId; }
    public UUID getTenantId() { return tenantId; }
    public CloseoutStatus getStatus() { return status; }
    public UUID getStartedBy() { return startedBy; }
    public Instant getStartedAt() { return startedAt; }
    public String getReceivingUnit() { return receivingUnit; }
    public UUID getReceivingOfficerId() { return receivingOfficerId; }
    public LocalDate getHandoverDate() { return handoverDate; }
    public String getHandoverNote() { return handoverNote; }
    public UUID getHandoverSubmittedBy() { return handoverSubmittedBy; }
    public Instant getHandoverSubmittedAt() { return handoverSubmittedAt; }
    public UUID getHandoverDecidedBy() { return handoverDecidedBy; }
    public Instant getHandoverDecidedAt() { return handoverDecidedAt; }
    public String getHandoverDecisionNote() { return handoverDecisionNote; }
    public LocalDate getDlpEnd() { return dlpEnd; }
    public UUID getClosedBy() { return closedBy; }
    public Instant getClosedAt() { return closedAt; }
    public String getClosureNote() { return closureNote; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
