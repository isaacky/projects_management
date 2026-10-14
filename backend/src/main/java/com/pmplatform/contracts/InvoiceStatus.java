package com.pmplatform.contracts;

/** Payment certificate flow: RECEIVED, CERTIFIED by the contract manager, APPROVED by finance, PAID; or REJECTED. */
public enum InvoiceStatus {
    RECEIVED,
    CERTIFIED,
    APPROVED,
    PAID,
    REJECTED
}
