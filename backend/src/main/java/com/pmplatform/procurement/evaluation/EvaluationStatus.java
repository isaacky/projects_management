package com.pmplatform.procurement.evaluation;

/**
 * <pre>
 *   SETUP ──appoint──▶ PRELIMINARY ──close──▶ TECHNICAL ──close──▶ FINANCIAL ──close──▶ REPORT
 *                         (no technical criteria: straight to FINANCIAL; no bid left: straight to REPORT)
 *   REPORT ──submit──▶ SIGNING ──all signed──▶ APPROVAL ──last step approves──▶ AWARDED | TERMINATED
 *                                                  └──returned──▶ REPORT (next round)
 * </pre>
 */
public enum EvaluationStatus {
    SETUP,
    PRELIMINARY,
    TECHNICAL,
    FINANCIAL,
    REPORT,
    SIGNING,
    APPROVAL,
    AWARDED,
    TERMINATED;

    public boolean isFinal() {
        return this == AWARDED || this == TERMINATED;
    }

    /** Stages where members enter their own marks. */
    public boolean isScoring() {
        return this == PRELIMINARY || this == TECHNICAL;
    }
}
