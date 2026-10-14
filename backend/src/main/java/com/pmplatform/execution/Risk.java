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

/** A project risk, scored on a 5x5 probability and impact matrix. */
@Entity
@Table(schema = "execution", name = "risks")
public class Risk extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskCategory category;

    @Column(nullable = false)
    private int probability;

    @Column(nullable = false)
    private int impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskResponse response;

    private String mitigation;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskStatus status;

    @Column(name = "closed_note")
    private String closedNote;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Risk() {
    }

    public Risk(UUID tenantId, UUID projectId, int number, UUID createdBy, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.number = number;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.status = RiskStatus.OPEN;
    }

    public void update(String title, String description, RiskCategory category, int probability, int impact,
            RiskResponse response, String mitigation, UUID ownerId, LocalDate dueDate, Instant now) {
        if (probability < 1 || probability > 5 || impact < 1 || impact > 5) {
            throw new BusinessRuleException("Probability and impact are from 1 to 5");
        }
        this.title = title.trim();
        this.description = blank(description);
        this.category = category;
        this.probability = probability;
        this.impact = impact;
        this.response = response;
        this.mitigation = blank(mitigation);
        this.ownerId = ownerId;
        this.dueDate = dueDate;
        touch(now);
    }

    public void close(String note, Instant now) {
        if (status == RiskStatus.CLOSED) {
            throw new BusinessRuleException("Risk " + number + " is already closed");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Say why the risk is closed");
        }
        status = RiskStatus.CLOSED;
        closedNote = note.trim();
        closedAt = now;
        touch(now);
    }

    public void reopen(Instant now) {
        status = RiskStatus.OPEN;
        closedAt = null;
        closedNote = null;
        touch(now);
    }

    public ExecutionRules.RiskLevel level() {
        return ExecutionRules.level(probability, impact);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public UUID getProjectId() { return projectId; }
    public int getNumber() { return number; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public RiskCategory getCategory() { return category; }
    public int getProbability() { return probability; }
    public int getImpact() { return impact; }
    public RiskResponse getResponse() { return response; }
    public String getMitigation() { return mitigation; }
    public UUID getOwnerId() { return ownerId; }
    public LocalDate getDueDate() { return dueDate; }
    public RiskStatus getStatus() { return status; }
    public String getClosedNote() { return closedNote; }
    public Instant getClosedAt() { return closedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
