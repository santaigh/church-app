package com.church.app.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested record does not exist, or exists outside the caller's church.
 *
 * <p>Tenant-scoped lookups deliberately raise this rather than a 403: telling a user of
 * one church that a member id "exists but is not yours" leaks the existence of other
 * churches' data. Not-found is the safer answer.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super("%s not found: %s".formatted(resourceName, identifier),
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
}
