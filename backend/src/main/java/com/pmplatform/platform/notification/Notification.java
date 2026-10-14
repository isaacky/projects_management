package com.pmplatform.platform.notification;

import com.pmplatform.platform.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** One message in a user's in-app inbox. */
@Entity
@Table(schema = "platform", name = "notifications")
public class Notification implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(nullable = false, updatable = false)
    private String type;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(updatable = false)
    private String body;

    @Column(updatable = false)
    private String link;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Transient
    private boolean isNew = true;

    protected Notification() {
    }

    Notification(UUID tenantId, UUID recipientId, String type, String title, String body,
            String link, Instant now) {
        this.id = UuidV7.generate();
        this.tenantId = tenantId;
        this.recipientId = recipientId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
        this.createdAt = now;
    }

    void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
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
    public UUID getRecipientId() { return recipientId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
