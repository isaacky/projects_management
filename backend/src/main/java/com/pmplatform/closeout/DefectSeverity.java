package com.pmplatform.closeout;

public enum DefectSeverity {
    MINOR,
    MAJOR,
    /** Stops the asset being used safely; blocks the handover while open. */
    CRITICAL
}
