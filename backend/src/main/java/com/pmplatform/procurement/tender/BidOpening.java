package com.pmplatform.procurement.tender;

import com.pmplatform.platform.persistence.TenantEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** The record of opening one envelope of a tender's bids, with who was present. */
@Entity
@Table(schema = "procurement", name = "bid_openings")
public class BidOpening extends TenantEntity {

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Envelope envelope;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private UUID openedBy;

    @Column(updatable = false)
    private String venue;

    @Column(updatable = false)
    private String notes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "procurement", name = "bid_opening_attendees",
            joinColumns = @JoinColumn(name = "opening_id"))
    @Column(name = "user_id")
    private Set<UUID> attendees = new HashSet<>();

    protected BidOpening() {
    }

    public BidOpening(UUID tenantId, UUID tenderId, Envelope envelope, Instant openedAt, UUID openedBy,
            String venue, String notes, Set<UUID> attendees) {
        super(tenantId, openedAt);
        this.tenderId = tenderId;
        this.envelope = envelope;
        this.openedAt = openedAt;
        this.openedBy = openedBy;
        this.venue = venue;
        this.notes = notes;
        this.attendees = new HashSet<>(attendees);
    }

    public UUID getTenderId() { return tenderId; }
    public Envelope getEnvelope() { return envelope; }
    public Instant getOpenedAt() { return openedAt; }
    public UUID getOpenedBy() { return openedBy; }
    public String getVenue() { return venue; }
    public String getNotes() { return notes; }
    public Set<UUID> getAttendees() { return Set.copyOf(attendees); }
}
