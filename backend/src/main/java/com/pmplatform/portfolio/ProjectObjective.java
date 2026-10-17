package com.pmplatform.portfolio;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Something the project is meant to achieve, from its business case, with how success will be judged.
 * Post-implementation reviews record whether each active objective was met.
 */
@Entity
@Table(schema = "portfolio", name = "project_objectives")
public class ProjectObjective extends TenantEntity {

    public static final int MAX_PER_PROJECT = 20;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(nullable = false)
    private String statement;

    @Column(name = "success_measure")
    private String successMeasure;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ObjectiveStatus status;

    @Column(name = "withdrawn_reason")
    private String withdrawnReason;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProjectObjective() {
    }

    public ProjectObjective(UUID tenantId, UUID projectId, int number, String statement, String successMeasure,
            UUID createdBy, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.number = number;
        this.status = ObjectiveStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = now;
        word(statement, successMeasure, now);
    }

    public void word(String statement, String successMeasure, Instant now) {
        if (status != ObjectiveStatus.ACTIVE) {
            throw new BusinessRuleException("O" + number + " is withdrawn; reinstate it before changing it");
        }
        this.statement = required(statement, 500, "The objective");
        this.successMeasure = optional(successMeasure, 500, "How success will be judged");
        touch(now);
    }

    public void withdraw(String reason, Instant now) {
        if (status != ObjectiveStatus.ACTIVE) {
            throw new BusinessRuleException("O" + number + " is already withdrawn");
        }
        this.withdrawnReason = required(reason, 2000, "The reason for withdrawing it");
        this.status = ObjectiveStatus.WITHDRAWN;
        touch(now);
    }

    public void reinstate(Instant now) {
        if (status != ObjectiveStatus.WITHDRAWN) {
            throw new BusinessRuleException("O" + number + " is not withdrawn");
        }
        this.status = ObjectiveStatus.ACTIVE;
        this.withdrawnReason = null;
        touch(now);
    }

    static String required(String s, int max, String what) {
        String v = optional(s, max, what);
        if (v == null) {
            throw new BusinessRuleException(what + " is required");
        }
        return v;
    }

    static String optional(String s, int max, String what) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.strip();
        if (v.length() > max) {
            throw new BusinessRuleException(what + " is longer than " + max + " characters");
        }
        return v;
    }

    public UUID getProjectId() { return projectId; }
    public int getNumber() { return number; }
    public String getStatement() { return statement; }
    public String getSuccessMeasure() { return successMeasure; }
    public ObjectiveStatus getStatus() { return status; }
    public String getWithdrawnReason() { return withdrawnReason; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
