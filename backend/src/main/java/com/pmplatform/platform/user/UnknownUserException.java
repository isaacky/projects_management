package com.pmplatform.platform.user;

public class UnknownUserException extends RuntimeException {

    public UnknownUserException() {
        super("Your account is not set up in this organisation. Ask an administrator to add you.");
    }
}
