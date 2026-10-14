package com.pmplatform.contracts;

import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.procurement.tender.StoredFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A file kept with a contract: the signed contract, a security, an inspection report, an invoice scan... */
@Entity
@Table(schema = "contracts", name = "documents")
public class ContractDocument {

    @Id
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DocumentKind kind;

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

    @Version
    private Long version;

    protected ContractDocument() {
    }

    public ContractDocument(UUID contractId, DocumentKind kind, UUID refId, StoredFile f, UUID uploadedBy,
            Instant uploadedAt) {
        this.id = UuidV7.generate();
        this.contractId = contractId;
        this.kind = kind;
        this.refId = refId;
        this.fileName = f.fileName();
        this.contentType = f.contentType();
        this.sizeBytes = f.sizeBytes();
        this.sha256 = f.sha256();
        this.storageKey = f.storageKey();
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public DocumentKind getKind() { return kind; }
    public UUID getRefId() { return refId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
}
