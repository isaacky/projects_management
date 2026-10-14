package com.pmplatform.procurement.evaluation;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    RETURNED,
    TERMINATED,
    /** Not reached: an earlier step returned the report. */
    SKIPPED
}
