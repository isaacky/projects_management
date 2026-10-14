package com.pmplatform.closeout;

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

/** A defect found at or after completion, to be put right by the contractor. See {@link DefectStatus}. */
@Entity
@Table(schema = "closeout", name = "defects")
public class Defect extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(nullable = false)
    private String title;

    private String description;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "reported_by", nullable = false, updatable = false)
    private UUID reportedBy;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "rectified_by")
    private UUID rectifiedBy;

    @Column(name = "rectified_at")
    private Instant rectifiedAt;

    @Column(name = "rectified_note")
    private String rectifiedNote;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_note")
    private String closedNote;

    protected Defect() {
    }

    public Defect(UUID tenantId, UUID projectId, int number, UUID reportedBy, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.number = number;
        this.reportedBy = reportedBy;
        this.reportedAt = now;
        this.status = DefectStatus.OPEN;
    }

    public void describe(UUID contractId, String title, String description, String location, DefectSeverity severity,
            LocalDate dueDate, Instant now) {
        if (!status.isOutstanding()) {
            throw new BusinessRuleException("D" + number + " is " + status.name().toLowerCase() + "; it cannot be changed");
        }
        if (severity == null) {
            throw new BusinessRuleException("Choose the severity");
        }
        this.contractId = contractId;
        this.title = Text.required(title, 200, "The defect");
        this.description = Text.optional(description, 4000, "The description");
        this.location = Text.optional(location, 200, "The location");
        this.severity = severity;
        this.dueDate = dueDate;
        touch(now);
    }

    public void rectified(UUID by, String note, Instant now) {
        if (status != DefectStatus.OPEN) {
            throw new BusinessRuleException("Only an open defect can be marked as put right");
        }
        String n = Text.required(note, 2000, "What was done");
        this.status = DefectStatus.RECTIFIED;
        this.rectifiedBy = by;
        this.rectifiedAt = now;
        this.rectifiedNote = n;
        touch(now);
    }

    /** Checked and accepted. */
    public void verify(UUID by, String note, Instant now) {
        if (status != DefectStatus.RECTIFIED) {
            throw new BusinessRuleException("Mark the defect as put right before closing it");
        }
        if (by.equals(rectifiedBy)) {
            throw new BusinessRuleException("Someone other than the person who recorded the fix checks it");
        }
        finish(DefectStatus.CLOSED, by, Text.optional(note, 2000, "The note"), now);
    }

    /** The fix did not hold or was not acceptable. */
    public void reopen(String note, Instant now) {
        if (status != DefectStatus.RECTIFIED) {
            throw new BusinessRuleException("Only a defect marked as put right can be reopened");
        }
        String n = Text.required(note, 2000, "Why the fix is not accepted");
        this.status = DefectStatus.OPEN;
        this.closedNote = n;
        touch(now);
    }

    public void waive(UUID by, String reason, Instant now) {
        if (!status.isOutstanding()) {
            throw new BusinessRuleException("D" + number + " is already " + status.name().toLowerCase());
        }
        finish(DefectStatus.WAIVED, by, Text.required(reason, 2000, "The reason for waiving it"), now);
    }

    public boolean isOverdue(LocalDate today) {
        return status == DefectStatus.OPEN && dueDate != null && dueDate.isBefore(today);
    }

    private void finish(DefectStatus to, UUID by, String note, Instant now) {
        this.status = to;
        this.closedBy = by;
        this.closedAt = now;
        this.closedNote = note;
        touch(now);
    }

    public UUID getProjectId() { return projectId; }
    public int getNumber() { return number; }
    public UUID getContractId() { return contractId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public DefectSeverity getSeverity() { return severity; }
    public DefectStatus getStatus() { return status; }
    public LocalDate getDueDate() { return dueDate; }
    public UUID getReportedBy() { return reportedBy; }
    public Instant getReportedAt() { return reportedAt; }
    public UUID getRectifiedBy() { return rectifiedBy; }
    public Instant getRectifiedAt() { return rectifiedAt; }
    public String getRectifiedNote() { return rectifiedNote; }
    public UUID getClosedBy() { return closedBy; }
    public Instant getClosedAt() { return closedAt; }
    public String getClosedNote() { return closedNote; }
}
