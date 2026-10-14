package com.pmplatform.benefits;

public enum BenefitStatus {
    ACTIVE,
    ACHIEVED,
    NOT_ACHIEVED,
    CANCELLED;

    public boolean isOpen() {
        return this == ACTIVE;
    }
}
