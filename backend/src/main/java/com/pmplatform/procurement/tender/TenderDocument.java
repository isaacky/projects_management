package com.pmplatform.procurement.tender;

import com.pmplatform.platform.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A tender document given to bidders (not sealed). */
@Entity
@Table(schema = "procurement", name = "tender_documents")
public class TenderDocument {

    @Id
    private UUID id;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Version
    private Long version;

    protected TenderDocument() {
    }

    public TenderDocument(UUID id, UUID tenderId, StoredFile f, UUID uploadedBy, Instant uploadedAt) {
        this.id = id == null ? UuidV7.generate() : id;
        this.tenderId = tenderId;
        this.fileName = f.fileName();
        this.contentType = f.contentType();
        this.sizeBytes = f.sizeBytes();
        this.sha256 = f.sha256();
        this.storageKey = f.storageKey();
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() { return id; }
    public UUID getTenderId() { return tenderId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
}
