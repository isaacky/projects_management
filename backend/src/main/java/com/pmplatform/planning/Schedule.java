package com.pmplatform.planning;

import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** A project's schedule: its start date and baseline. One per project (the id is the project id). */
@Entity
@Table(schema = "planning", name = "schedules")
public class Schedule implements Persistable<UUID> {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "baseline_set_at")
    private Instant baselineSetAt;

    @Column(name = "baseline_set_by")
    private UUID baselineSetBy;

    @Column(name = "baseline_finish")
    private LocalDate baselineFinish;

    @Column(name = "baseline_note")
    private String baselineNote;

    @Column(name = "baseline_count", nullable = false)
    private int baselineCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @Transient
    private boolean isNew = true;

    protected Schedule() {
    }

    public Schedule(UUID tenantId, UUID projectId, LocalDate startDate, Instant now) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.startDate = startDate;
        this.updatedAt = now;
    }

    public void setStartDate(LocalDate start, Instant now) {
        this.startDate = start;
        this.updatedAt = now;
    }

    public void baselined(UUID by, LocalDate finish, String note, Instant now) {
        if (baselineCount > 0 && (note == null || note.isBlank())) {
            throw new BusinessRuleException("Give the reason for re-baselining");
        }
        this.baselineSetAt = now;
        this.baselineSetBy = by;
        this.baselineFinish = finish;
        this.baselineNote = note == null || note.isBlank() ? null : note.trim();
        this.baselineCount++;
        this.updatedAt = now;
    }

    /** The plan was replaced wholesale: the baseline no longer applies. The count is kept, so re-baselining stays with the PMO. */
    public void clearBaseline(Instant now) {
        this.baselineSetAt = null;
        this.baselineSetBy = null;
        this.baselineFinish = null;
        this.baselineNote = null;
        this.updatedAt = now;
    }

    public void changed(Instant now) {
        this.updatedAt = now;
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
    public LocalDate getStartDate() { return startDate; }
    public Instant getBaselineSetAt() { return baselineSetAt; }
    public UUID getBaselineSetBy() { return baselineSetBy; }
    public LocalDate getBaselineFinish() { return baselineFinish; }
    public String getBaselineNote() { return baselineNote; }
    public int getBaselineCount() { return baselineCount; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
