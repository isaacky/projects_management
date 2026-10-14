package com.pmplatform.contracts;

/** HELD until released to the supplier or called (cashed) by the entity. */
public enum SecurityStatus {
    HELD,
    RELEASED,
    CALLED
}
