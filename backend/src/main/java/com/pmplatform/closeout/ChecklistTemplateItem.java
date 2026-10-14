package com.pmplatform.closeout;

import com.pmplatform.platform.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One item of the organisation's standard completion checklist. */
@Entity
@Table(schema = "closeout", name = "checklist_templates")
public class ChecklistTemplateItem extends TenantEntity {

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "responsible_role")
    private String responsibleRole;

    @Column(name = "evidence_required", nullable = false)
    private boolean evidenceRequired;

    protected ChecklistTemplateItem() {
    }

    public ChecklistTemplateItem(UUID tenantId, int sortOrder, String title, String description, String responsibleRole,
            boolean evidenceRequired, Instant now) {
        super(tenantId, now);
        this.sortOrder = sortOrder;
        this.title = Text.required(title, 200, "The item");
        this.description = Text.optional(description, 1000, "The description");
        this.responsibleRole = Text.optional(responsibleRole, 64, "The role");
        this.evidenceRequired = evidenceRequired;
    }

    public int getSortOrder() { return sortOrder; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getResponsibleRole() { return responsibleRole; }
    public boolean isEvidenceRequired() { return evidenceRequired; }
}
