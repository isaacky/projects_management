package com.pmplatform.procurement.requisition;

public enum RequisitionStatus {
    /** Being prepared by the requester; can be edited. */
    DRAFT,
    /** In the approval chain. */
    SUBMITTED,
    /** Every approval step passed; ready for procurement. */
    APPROVED,
    /** Stopped by an approver. Final. */
    REJECTED,
    /** Sent back to the requester for changes; can be edited and resubmitted. */
    RETURNED,
    /** Withdrawn by the requester. Final. */
    CANCELLED
}
