package com.church.app.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a create/update would collide with an existing record on a unique key --
 * a repeated family code, member code, username or receipt number.
 */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }

    public DuplicateResourceException(String resourceName, String field, Object value) {
        super("%s already exists with %s = %s".formatted(resourceName, field, value),
                HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }
}
