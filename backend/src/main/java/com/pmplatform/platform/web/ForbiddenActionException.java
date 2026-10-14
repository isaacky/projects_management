package com.pmplatform.platform.web;

/** The user is known but may not do this (wrong role, or their own request). */
public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
