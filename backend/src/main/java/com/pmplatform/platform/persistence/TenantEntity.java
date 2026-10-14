package com.pmplatform.platform.persistence;

import com.pmplatform.platform.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Base for tenant-owned rows: UUIDv7 id assigned in Java, the owning tenant, an
 * updated-at stamp and an optimistic-lock version (two people editing the same row
 * cannot silently overwrite each other).
 */
@MappedSuperclass
public abstract class TenantEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @Transient
    private boolean isNew = true;

    protected TenantEntity() {
    }

    protected TenantEntity(UUID tenantId, Instant now) {
        this.id = UuidV7.generate();
        this.tenantId = tenantId;
        this.updatedAt = now;
    }

    protected void touch(Instant now) {
        this.updatedAt = now;
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

    @Override
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
