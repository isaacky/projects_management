package com.pmplatform.contracts;

/** DRAFT until signed; ACTIVE while being delivered; then COMPLETED or TERMINATED. CANCELLED = never signed. */
public enum ContractStatus {
    DRAFT,
    ACTIVE,
    COMPLETED,
    TERMINATED,
    CANCELLED
}
