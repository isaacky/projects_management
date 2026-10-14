package com.pmplatform.closeout;

/**
 * <pre>
 *   OPEN ──contractor fixes──▶ RECTIFIED ──checked──▶ CLOSED
 *     ▲                            │
 *     └──────── reopened ◀─────────┘
 *   OPEN or RECTIFIED ──PMO lead──▶ WAIVED
 * </pre>
 */
public enum DefectStatus {
    OPEN,
    RECTIFIED,
    CLOSED,
    WAIVED;

    public boolean isOutstanding() {
        return this == OPEN || this == RECTIFIED;
    }
}
