package com.pmplatform.closeout;

/**
 * <pre>
 *   OPEN ──submit handover──▶ HANDOVER_SUBMITTED ──receiving officer accepts──▶ HANDED_OVER ──PMO closes──▶ CLOSED
 *     ▲                              │
 *     └────────── rejected ◀─────────┘
 * </pre>
 */
public enum CloseoutStatus {
    OPEN,
    HANDOVER_SUBMITTED,
    HANDED_OVER,
    CLOSED
}
