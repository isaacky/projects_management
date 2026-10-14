package com.pmplatform.procurement.tender;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A call for bids raised from an approved requisition.
 *
 * <pre>
 *   DRAFT ──publish──▶ PUBLISHED ──closing time passes──▶ (closed, still sealed)
 *     │                    │        ──open──▶ OPENED                      (single envelope)
 *     │                    │        ──open technical──▶ TECHNICAL_OPENED ──open financial──▶ OPENED
 *     └──── cancel ◀───────┘  (any time before the first opening)
 *
 *   OPENED ──award approved──▶ AWARDED
 *   TECHNICAL_OPENED / OPENED ──no award approved──▶ TERMINATED
 * </pre>
 *
 * Method rules (bid period, advertising, envelopes, opening quorum) are copied from
 * the procurement settings at creation so later settings changes do not move the goalposts.
 */
@Entity
@Table(schema = "procurement", name = "tenders")
public class Tender extends TenantEntity {

    @Embeddable
    public static class Invitation {

        @Column(name = "supplier_id", nullable = false)
        private UUID supplierId;

        @Column(name = "invited_at", nullable = false)
        private Instant invitedAt;

        protected Invitation() {
        }

        Invitation(UUID supplierId, Instant invitedAt) {
            this.supplierId = supplierId;
            this.invitedAt = invitedAt;
        }

        public UUID getSupplierId() { return supplierId; }
        public Instant getInvitedAt() { return invitedAt; }
    }

    @Column(nullable = false, updatable = false)
    private String number;

    @Column(name = "requisition_id", nullable = false, updatable = false)
    private UUID requisitionId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "method_code", nullable = false, updatable = false)
    private String methodCode;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "estimated_value_minor", nullable = false, updatable = false)
    private long estimatedValueMinor;

    @Column(name = "two_envelope", nullable = false, updatable = false)
    private boolean twoEnvelope;

    @Column(name = "min_bid_period_days", nullable = false, updatable = false)
    private int minBidPeriodDays;

    @Column(name = "opening_quorum", nullable = false, updatable = false)
    private int openingQuorum;

    @Column(name = "invited_only", nullable = false, updatable = false)
    private boolean invitedOnly;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenderStatus status;

    @Column(name = "closing_at")
    private Instant closingAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cancelled_reason")
    private String cancelledReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "reserved_group")
    private ReservedGroup reservedGroup;

    /** True once the envelope holding prices (single or financial) has been opened. */
    @Column(name = "prices_opened", nullable = false)
    private boolean pricesOpened;

    @Column(name = "awarded_bid_id")
    private UUID awardedBidId;

    @Column(name = "awarded_amount_minor")
    private Long awardedAmountMinor;

    @Column(name = "awarded_at")
    private Instant awardedAt;

    @Column(name = "standstill_ends_at")
    private Instant standstillEndsAt;

    @Column(name = "wrapped_key", nullable = false, updatable = false)
    private byte[] wrappedKey;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "procurement", name = "tender_invitations",
            joinColumns = @JoinColumn(name = "tender_id"))
    private List<Invitation> invitations = new ArrayList<>();

    protected Tender() {
    }

    public Tender(UUID tenantId, String number, UUID requisitionId, UUID projectId, String title,
            String description, String methodCode, String currency, long estimatedValueMinor, boolean twoEnvelope,
            int minBidPeriodDays, int openingQuorum, boolean invitedOnly, byte[] wrappedKey, UUID createdBy,
            Instant now) {
        super(tenantId, now);
        this.number = number;
        this.requisitionId = requisitionId;
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.methodCode = methodCode;
        this.currency = currency;
        this.estimatedValueMinor = estimatedValueMinor;
        this.twoEnvelope = twoEnvelope;
        this.minBidPeriodDays = minBidPeriodDays;
        this.openingQuorum = openingQuorum;
        this.invitedOnly = invitedOnly;
        this.wrappedKey = wrappedKey.clone();
        this.createdBy = createdBy;
        this.createdAt = now;
        this.status = TenderStatus.DRAFT;
    }

    // ------------------------------------------------------------------ preparation

    public void edit(String title, String description, Instant closingAt, Instant now) {
        requireStatus(TenderStatus.DRAFT, "edit");
        this.title = title;
        this.description = description;
        this.closingAt = closingAt;
        touch(now);
    }

    /** Reserves the tender for an AGPO group, or opens it to all (null). Only while a draft. */
    public void reserveFor(ReservedGroup group, Instant now) {
        requireStatus(TenderStatus.DRAFT, "change its reservation");
        this.reservedGroup = group;
        touch(now);
    }

    public void invite(UUID supplierId, Instant now) {
        if (status != TenderStatus.DRAFT && !acceptingBids(now)) {
            throw new BusinessRuleException("Suppliers can only be invited before the tender closes");
        }
        if (isInvited(supplierId)) {
            return;
        }
        invitations.add(new Invitation(supplierId, now));
        touch(now);
    }

    public void uninvite(UUID supplierId, Instant now) {
        requireStatus(TenderStatus.DRAFT, "remove invited suppliers from");
        invitations.removeIf(i -> i.getSupplierId().equals(supplierId));
        touch(now);
    }

    public boolean isInvited(UUID supplierId) {
        return invitations.stream().anyMatch(i -> i.getSupplierId().equals(supplierId));
    }

    /** Earliest closing time allowed if published now. */
    public Instant earliestClosing(Instant now) {
        return now.plus(Duration.ofDays(minBidPeriodDays));
    }

    public void publish(Instant now) {
        requireStatus(TenderStatus.DRAFT, "publish");
        if (closingAt == null) {
            throw new BusinessRuleException("Set the closing date and time before publishing");
        }
        if (closingAt.isBefore(earliestClosing(now))) {
            throw new BusinessRuleException("This method needs a bid period of at least " + minBidPeriodDays
                    + " days; the closing time must be on or after " + earliestClosing(now));
        }
        if (invitedOnly && invitations.isEmpty()) {
            throw new BusinessRuleException("This method is not advertised: invite at least one supplier first");
        }
        status = TenderStatus.PUBLISHED;
        publishedAt = now;
        touch(now);
    }

    /** Moves the closing time later (for example after an addendum). Never earlier. */
    public void extendClosing(Instant newClosing, Instant now) {
        if (!acceptingBids(now)) {
            throw new BusinessRuleException("Only an open tender's closing time can be extended");
        }
        if (!newClosing.isAfter(closingAt)) {
            throw new BusinessRuleException("The new closing time must be later than the current one");
        }
        closingAt = newClosing;
        touch(now);
    }

    public void cancel(String reason, Instant now) {
        if (status != TenderStatus.DRAFT && status != TenderStatus.PUBLISHED) {
            throw new BusinessRuleException("A tender can only be cancelled before bids are opened");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Give the reason for cancelling the tender");
        }
        status = TenderStatus.CANCELLED;
        cancelledReason = reason.trim();
        touch(now);
    }

    // ------------------------------------------------------------------ bids and opening

    public boolean acceptingBids(Instant now) {
        return status == TenderStatus.PUBLISHED && now.isBefore(closingAt);
    }

    public boolean isClosed(Instant now) {
        return status == TenderStatus.PUBLISHED && !now.isBefore(closingAt);
    }

    /** Checks that a supplier may bid now; returns nothing, throws if not. */
    public void requireAcceptingBidFrom(UUID supplierId, Instant now) {
        if (!acceptingBids(now)) {
            throw new BusinessRuleException(status == TenderStatus.PUBLISHED
                    ? "Bidding closed at " + closingAt + "; late bids cannot be received"
                    : "This tender is not open for bids");
        }
        if (invitedOnly && !isInvited(supplierId)) {
            throw new BusinessRuleException("Only invited suppliers may bid on this tender");
        }
    }

    /** For a reserved tender: the supplier must hold a valid certificate for the group. */
    public void requireEligible(com.pmplatform.procurement.supplier.Supplier s, java.time.LocalDate day) {
        if (reservedGroup == null) {
            return;
        }
        if (!s.agpoValidOn(day) || !reservedGroup.admits(s.getAgpoCategory())) {
            throw new BusinessRuleException("This tender is reserved for " + reservedGroup.label() + "; "
                    + s.getName() + " has no valid AGPO certificate for that group"
                    + (s.getAgpoCategory() == null ? "" : " (registered: " + s.getAgpoCategory()
                    + (s.getAgpoExpiry() == null ? "" : ", expires " + s.getAgpoExpiry()) + ")"));
        }
    }

    public List<Envelope> envelopes() {
        return twoEnvelope ? List.of(Envelope.TECHNICAL, Envelope.FINANCIAL) : List.of(Envelope.SINGLE);
    }

    /** The envelope that would be opened next, if any. */
    public Envelope nextEnvelopeToOpen() {
        return switch (status) {
            case PUBLISHED -> twoEnvelope ? Envelope.TECHNICAL : Envelope.SINGLE;
            case TECHNICAL_OPENED -> Envelope.FINANCIAL;
            default -> null;
        };
    }

    public void open(Envelope envelope, int attendees, Instant now) {
        if (envelope != nextEnvelopeToOpen()) {
            throw new BusinessRuleException(nextEnvelopeToOpen() == null
                    ? "There is nothing left to open on this tender"
                    : "The next envelope to open is " + nextEnvelopeToOpen());
        }
        if (now.isBefore(closingAt)) {
            throw new BusinessRuleException("Bids cannot be opened before the closing time (" + closingAt + ")");
        }
        if (attendees < openingQuorum) {
            throw new BusinessRuleException("At least " + openingQuorum + " people must attend the opening; "
                    + attendees + " recorded");
        }
        status = envelope == Envelope.TECHNICAL ? TenderStatus.TECHNICAL_OPENED : TenderStatus.OPENED;
        if (envelope != Envelope.TECHNICAL) {
            pricesOpened = true;
        }
        touch(now);
    }

    /** At least one envelope has been opened. */
    public boolean anyOpened() {
        return status == TenderStatus.TECHNICAL_OPENED || status == TenderStatus.OPENED
                || status == TenderStatus.AWARDED || status == TenderStatus.TERMINATED;
    }

    public boolean isOpen(Envelope envelope) {
        return switch (envelope) {
            case SINGLE, FINANCIAL -> pricesOpened;
            case TECHNICAL -> anyOpened();
        };
    }

    /** Prices are read out once the envelope holding them is open. */
    public boolean pricesOpen() {
        return pricesOpened;
    }

    // ------------------------------------------------------------------ outcome

    /** Records the approved award; the contract may be signed once the standstill period ends. */
    public void award(UUID bidId, long amountMinor, int standstillDays, Instant now) {
        requireStatus(TenderStatus.OPENED, "be awarded");
        status = TenderStatus.AWARDED;
        awardedBidId = bidId;
        awardedAmountMinor = amountMinor;
        awardedAt = now;
        standstillEndsAt = now.plus(Duration.ofDays(standstillDays));
        touch(now);
    }

    /** Ends a tender after opening without an award. */
    public void terminate(String reason, Instant now) {
        if (status != TenderStatus.TECHNICAL_OPENED && status != TenderStatus.OPENED) {
            throw new BusinessRuleException("Only an opened tender that has not been awarded can be terminated");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Give the reason for terminating the tender");
        }
        status = TenderStatus.TERMINATED;
        cancelledReason = reason.trim();
        touch(now);
    }

    private void requireStatus(TenderStatus expected, String verb) {
        if (status != expected) {
            throw new BusinessRuleException("A tender that is " + status + " cannot " + verb);
        }
    }

    public String getNumber() { return number; }
    public UUID getRequisitionId() { return requisitionId; }
    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getMethodCode() { return methodCode; }
    public String getCurrency() { return currency; }
    public long getEstimatedValueMinor() { return estimatedValueMinor; }
    public boolean isTwoEnvelope() { return twoEnvelope; }
    public int getMinBidPeriodDays() { return minBidPeriodDays; }
    public int getOpeningQuorum() { return openingQuorum; }
    public boolean isInvitedOnly() { return invitedOnly; }
    public TenderStatus getStatus() { return status; }
    public Instant getClosingAt() { return closingAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getCancelledReason() { return cancelledReason; }
    public ReservedGroup getReservedGroup() { return reservedGroup; }
    public UUID getAwardedBidId() { return awardedBidId; }
    public Long getAwardedAmountMinor() { return awardedAmountMinor; }
    public Instant getAwardedAt() { return awardedAt; }
    public Instant getStandstillEndsAt() { return standstillEndsAt; }
    public byte[] getWrappedKey() { return wrappedKey.clone(); }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Invitation> getInvitations() { return List.copyOf(invitations); }
}
