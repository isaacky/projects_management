package com.pmplatform.planning;

import com.pmplatform.platform.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** A link between two activities. */
@Entity
@Table(schema = "planning", name = "dependencies")
public class Dependency {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "predecessor_id", nullable = false, updatable = false)
    private UUID predecessorId;

    @Column(name = "successor_id", nullable = false, updatable = false)
    private UUID successorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DependencyType type;

    @Column(name = "lag_days", nullable = false, updatable = false)
    private int lagDays;

    @Version
    private Long version;

    protected Dependency() {
    }

    public Dependency(UUID projectId, UUID predecessorId, UUID successorId, DependencyType type, int lagDays) {
        this.id = UuidV7.generate();
        this.projectId = projectId;
        this.predecessorId = predecessorId;
        this.successorId = successorId;
        this.type = type;
        this.lagDays = lagDays;
    }

    public ScheduleEngine.Link toLink() {
        return new ScheduleEngine.Link(predecessorId, successorId, type, lagDays);
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getPredecessorId() { return predecessorId; }
    public UUID getSuccessorId() { return successorId; }
    public DependencyType getType() { return type; }
    public int getLagDays() { return lagDays; }
}
