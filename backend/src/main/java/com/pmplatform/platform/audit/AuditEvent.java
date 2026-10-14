package com.pmplatform.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * One immutable entry in the append-only audit log. Each entry stores the hash of
 * the previous entry for the same tenant, so any edit or deletion breaks the chain.
 */
@Entity
@Table(schema = "platform", name = "audit_events")
public class AuditEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    /** Database-assigned insertion order; the chain follows this column. */
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false, updatable = false)
    private String actor;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    /** JSON text, hashed exactly as stored. */
    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "prev_hash", updatable = false)
    private String prevHash;

    @Column(nullable = false, updatable = false)
    private String hash;

    @Transient
    private boolean isNew = true;

    protected AuditEvent() {
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    AuditEvent(UUID id, UUID tenantId, Instant occurredAt, String actor, String action,
            String entityType, UUID entityId, String payload, String prevHash, String hash) {
        this.id = id;
        this.tenantId = tenantId;
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.payload = payload;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    @Override
    public UUID getId() { return id; }
    public Long getSeq() { return seq; }
    public UUID getTenantId() { return tenantId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getPayload() { return payload; }
    public String getPrevHash() { return prevHash; }
    public String getHash() { return hash; }
}
