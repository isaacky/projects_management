package com.pmplatform.closeout;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.procurement.tender.StoredFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A file attached to a closeout: checklist evidence, defect photos, or handover documents. */
@Entity
@Table(schema = "closeout", name = "documents")
public class CloseoutDocument extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CloseoutDocumentKind kind;

    @Column(name = "ref_id", updatable = false)
    private UUID refId;

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

    protected CloseoutDocument() {
    }

    public CloseoutDocument(UUID tenantId, UUID projectId, CloseoutDocumentKind kind, UUID refId, StoredFile f,
            UUID uploadedBy, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.kind = kind;
        this.refId = refId;
        this.fileName = f.fileName();
        this.contentType = f.contentType();
        this.sizeBytes = f.sizeBytes();
        this.sha256 = f.sha256();
        this.storageKey = f.storageKey();
        this.uploadedBy = uploadedBy;
        this.uploadedAt = now;
    }

    public UUID getProjectId() { return projectId; }
    public CloseoutDocumentKind getKind() { return kind; }
    public UUID getRefId() { return refId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
}
