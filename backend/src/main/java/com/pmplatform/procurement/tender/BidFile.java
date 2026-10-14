package com.pmplatform.procurement.tender;

import com.pmplatform.platform.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** One sealed file of a bid, stored encrypted. */
@Entity
@Table(schema = "procurement", name = "bid_files")
public class BidFile {

    @Id
    private UUID id;

    @Column(name = "bid_id", nullable = false, updatable = false)
    private UUID bidId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Envelope envelope;

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

    @Column(name = "file_nonce", nullable = false, updatable = false)
    private byte[] fileNonce;

    @Column(name = "added_after_opening", nullable = false, updatable = false)
    private boolean addedAfterOpening;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Version
    private Long version;

    protected BidFile() {
    }

    public BidFile(UUID id, UUID bidId, Envelope envelope, StoredFile f, boolean addedAfterOpening,
            UUID uploadedBy, Instant uploadedAt) {
        this.id = id == null ? UuidV7.generate() : id;
        this.bidId = bidId;
        this.envelope = envelope;
        this.fileName = f.fileName();
        this.contentType = f.contentType();
        this.sizeBytes = f.sizeBytes();
        this.sha256 = f.sha256();
        this.storageKey = f.storageKey();
        this.fileNonce = f.fileNonce().clone();
        this.addedAfterOpening = addedAfterOpening;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() { return id; }
    public UUID getBidId() { return bidId; }
    public Envelope getEnvelope() { return envelope; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public byte[] getFileNonce() { return fileNonce.clone(); }
    public boolean isAddedAfterOpening() { return addedAfterOpening; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
}
