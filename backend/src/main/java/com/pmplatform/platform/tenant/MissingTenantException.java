package com.pmplatform.platform.tenant;

public class MissingTenantException extends RuntimeException {

    public MissingTenantException() {
        super("No tenant could be resolved for this request");
    }
}
