package com.pmplatform.procurement.supplier;

public enum SupplierStatus {
    ACTIVE,
    /** Temporarily barred, e.g. pending documents. Cannot bid. */
    SUSPENDED,
    /** Barred by PPRA or the organisation. Cannot bid. */
    DEBARRED
}
