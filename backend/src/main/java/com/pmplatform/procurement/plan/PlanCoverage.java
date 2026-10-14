package com.pmplatform.procurement.plan;

/** How a submitted requisition relates to the annual procurement plan. */
public enum PlanCoverage {
    /** Names an approved plan line. */
    PLANNED,
    /** An approved plan covered the date but the purchase is not in it. */
    UNPLANNED,
    /** No approved plan for that financial year. */
    NO_PLAN
}
