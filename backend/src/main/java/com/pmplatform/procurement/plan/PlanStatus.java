package com.pmplatform.procurement.plan;

/**
 * <pre>
 *   DRAFT ──submit──▶ SUBMITTED ──approve──▶ APPROVED
 *     ▲                  │                      │
 *     └──── return ◀─────┘                      │
 *     └──────────────── revise ◀────────────────┘
 * </pre>
 */
public enum PlanStatus {
    /** Departments and procurement are entering lines (first time, or a revision). */
    DRAFT,
    /** With the accounting officer. */
    SUBMITTED,
    APPROVED
}
