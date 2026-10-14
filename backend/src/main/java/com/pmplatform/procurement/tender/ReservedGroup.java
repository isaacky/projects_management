package com.pmplatform.procurement.tender;

import com.pmplatform.procurement.supplier.AgpoCategory;

/**
 * Tenders reserved under Access to Government Procurement Opportunities (AGPO):
 * only suppliers holding a valid certificate for the group may bid.
 */
public enum ReservedGroup {
    YOUTH,
    WOMEN,
    PWD,
    /** Any of the three AGPO groups. */
    AGPO;

    public boolean admits(AgpoCategory category) {
        return category != null && (this == AGPO || name().equals(category.name()));
    }

    public String label() {
        return switch (this) {
            case YOUTH -> "youth";
            case WOMEN -> "women";
            case PWD -> "persons with disabilities";
            case AGPO -> "AGPO (youth, women or persons with disabilities)";
        };
    }
}
