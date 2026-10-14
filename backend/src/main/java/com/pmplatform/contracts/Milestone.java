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
import java.time.LocalDate;
import java.util.UUID;

/** A deliverable of the contract with its value in the payment schedule. */
@Entity
@Table(schema = "contracts", name = "milestones")
public class Milestone {

    @Id
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MilestoneStatus status;

    @Column(name = "accepted_at")
    private LocalDate acceptedAt;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "acceptance_note")
    private String acceptanceNote;

    @Version
    private Long version;

    protected Milestone() {
    }

    public Milestone(UUID contractId, int sortOrder, String title, String description, LocalDate dueDate,
            long amountMinor) {
        this.id = UuidV7.generate();
        this.contractId = contractId;
        this.status = MilestoneStatus.PLANNED;
        edit(sortOrder, title, description, dueDate, amountMinor);
    }

    public void edit(int sortOrder, String title, String description, LocalDate dueDate, long amountMinor) {
        if (status != MilestoneStatus.PLANNED) {
            throw new BusinessRuleException("Only planned milestones can be changed");
        }
        if (amountMinor < 0) {
            throw new BusinessRuleException("A milestone value cannot be negative");
        }
        this.sortOrder = sortOrder;
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.dueDate = dueDate;
        this.amountMinor = amountMinor;
    }

    /** Recorded after inspection: the deliverable meets the contract. */
    public void accept(LocalDate on, UUID by, String note) {
        if (status != MilestoneStatus.PLANNED) {
            throw new BusinessRuleException("Milestone \"" + title + "\" is already " + status.name().toLowerCase());
        }
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Record the inspection or acceptance details");
        }
        status = MilestoneStatus.ACCEPTED;
        acceptedAt = on;
        acceptedBy = by;
        acceptanceNote = note.trim();
    }

    public void cancel() {
        if (status != MilestoneStatus.PLANNED) {
            throw new BusinessRuleException("Only planned milestones can be cancelled");
        }
        status = MilestoneStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public int getSortOrder() { return sortOrder; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getDueDate() { return dueDate; }
    public long getAmountMinor() { return amountMinor; }
    public MilestoneStatus getStatus() { return status; }
    public LocalDate getAcceptedAt() { return acceptedAt; }
    public UUID getAcceptedBy() { return acceptedBy; }
    public String getAcceptanceNote() { return acceptanceNote; }
}
