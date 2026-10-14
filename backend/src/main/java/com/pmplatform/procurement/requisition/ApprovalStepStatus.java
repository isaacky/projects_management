package com.pmplatform.procurement.requisition;

public enum ApprovalStepStatus {
    /** A later step; not yet this approver's turn. */
    WAITING,
    /** This approver's turn now. */
    PENDING,
    APPROVED,
    REJECTED,
    RETURNED,
    /** No longer needed (an earlier step rejected or returned, or the requester withdrew). */
    CANCELLED
}
