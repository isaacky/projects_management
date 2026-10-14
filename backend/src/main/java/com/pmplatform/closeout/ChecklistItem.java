package com.pmplatform.closeout;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One item of a project's completion checklist. */
@Entity
@Table(schema = "closeout", name = "checklist_items")
public class ChecklistItem extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "responsible_role")
    private String responsibleRole;

    @Column(name = "evidence_required", nullable = false)
    private boolean evidenceRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    private String note;

    @Column(name = "done_by")
    private UUID doneBy;

    @Column(name = "done_at")
    private Instant doneAt;

    protected ChecklistItem() {
    }

    public ChecklistItem(UUID tenantId, UUID projectId, int sortOrder, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.sortOrder = sortOrder;
        this.status = ItemStatus.OPEN;
    }

    public void define(String title, String description, String responsibleRole, boolean evidenceRequired, Instant now) {
        this.title = Text.required(title, 200, "The item");
        this.description = Text.optional(description, 1000, "The description");
        this.responsibleRole = Text.optional(responsibleRole, 64, "The role");
        this.evidenceRequired = evidenceRequired;
        touch(now);
    }

    /** evidenceCount: documents attached to this item. */
    public void done(UUID by, String note, int evidenceCount, Instant now) {
        if (status != ItemStatus.OPEN) {
            throw new BusinessRuleException("\"" + title + "\" is already " + label());
        }
        if (evidenceRequired && evidenceCount == 0) {
            throw new BusinessRuleException("Attach the evidence for \"" + title + "\" first");
        }
        String n = Text.optional(note, 2000, "The note");
        this.status = ItemStatus.DONE;
        mark(by, n, now);
    }

    public void notApplicable(UUID by, String reason, Instant now) {
        if (status != ItemStatus.OPEN) {
            throw new BusinessRuleException("\"" + title + "\" is already " + label());
        }
        String n = Text.required(reason, 2000, "The reason it does not apply");
        this.status = ItemStatus.NOT_APPLICABLE;
        mark(by, n, now);
    }

    public void reopen(Instant now) {
        this.status = ItemStatus.OPEN;
        this.note = null;
        this.doneBy = null;
        this.doneAt = null;
        touch(now);
    }

    public boolean isOpen() {
        return status == ItemStatus.OPEN;
    }

    private String label() {
        return status == ItemStatus.DONE ? "done" : "marked not applicable";
    }

    private void mark(UUID by, String note, Instant now) {
        this.note = note;
        this.doneBy = by;
        this.doneAt = now;
        touch(now);
    }

    public UUID getProjectId() { return projectId; }
    public int getSortOrder() { return sortOrder; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getResponsibleRole() { return responsibleRole; }
    public boolean isEvidenceRequired() { return evidenceRequired; }
    public ItemStatus getStatus() { return status; }
    public String getNote() { return note; }
    public UUID getDoneBy() { return doneBy; }
    public Instant getDoneAt() { return doneAt; }
}
