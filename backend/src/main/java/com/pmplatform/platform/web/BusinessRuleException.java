package com.pmplatform.platform.web;

/** A request that is well-formed but breaks a domain rule (for example skipping a gate). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
