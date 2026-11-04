package com.shopsphere.common.exception;

public class ResourceNotFound extends BusinessException {
    public ResourceNotFound(String resource) {
        super(404, "NOT_FOUND", resource + " not found");
    }

    public static ResourceNotFound of(String resource) {
        return new ResourceNotFound(resource);
    }
}
