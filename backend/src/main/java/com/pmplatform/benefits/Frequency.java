package com.pmplatform.benefits;

/** How often the benefit owner records a measurement. */
public enum Frequency {
    MONTHLY(1),
    QUARTERLY(3),
    SEMI_ANNUAL(6),
    ANNUAL(12);

    private final int months;

    Frequency(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
