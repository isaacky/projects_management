package com.pmplatform.procurement.tender;

public enum TenderStatus {
    /** Being prepared; documents and invitations can change. */
    DRAFT,
    /** Open for bids until the closing time; closed (but still sealed) after it. */
    PUBLISHED,
    /** Two-envelope tenders: technical envelopes opened, financial still sealed. */
    TECHNICAL_OPENED,
    /** Every envelope opened; under evaluation. */
    OPENED,
    /** Award approved after evaluation; the standstill period runs before a contract is signed. */
    AWARDED,
    /** Ended after opening without an award (no responsive bid, or the accounting officer terminated it). */
    TERMINATED,
    /** Withdrawn before any bid was opened. */
    CANCELLED
}
