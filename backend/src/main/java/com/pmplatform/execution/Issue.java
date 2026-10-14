package com.pmplatform.execution;

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

/** Something that has gone wrong and needs action (possibly a risk that materialised). */
@Entity
@Table(schema = "execution", name = "issues")
public class Issue extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    private String resolution;

    @Column(name = "risk_id")
    private UUID riskId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Issue() {
    }

    public Issue(UUID tenantId, UUID projectId, int number, UUID createdBy, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.number = number;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.status = IssueStatus.OPEN;
    }

    public void update(String title, String description, IssuePriority priority, IssueStatus status, UUID ownerId,
            LocalDate dueDate, String resolution, UUID riskId, Instant now) {
        boolean resolving = status == IssueStatus.RESOLVED || status == IssueStatus.CLOSED;
        if (resolving && (resolution == null || resolution.isBlank())) {
            throw new BusinessRuleException("Record how the issue was resolved");
        }
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.priority = priority;
        this.status = status;
        this.ownerId = ownerId;
        this.dueDate = dueDate;
        this.resolution = resolution == null || resolution.isBlank() ? null : resolution.trim();
        this.riskId = riskId;
        touch(now);
    }

    public boolean isOpen() {
        return status == IssueStatus.OPEN || status == IssueStatus.IN_PROGRESS;
    }

    public UUID getProjectId() { return projectId; }
    public int getNumber() { return number; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public IssuePriority getPriority() { return priority; }
    public IssueStatus getStatus() { return status; }
    public UUID getOwnerId() { return ownerId; }
    public LocalDate getDueDate() { return dueDate; }
    public String getResolution() { return resolution; }
    public UUID getRiskId() { return riskId; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
