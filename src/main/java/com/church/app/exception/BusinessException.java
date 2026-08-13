package com.church.app.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for failures that are expected outcomes of a business rule rather than defects.
 *
 * <p>These carry their own HTTP status and a stable machine-readable {@code code}, and are
 * logged at WARN without a stack trace -- a duplicate family code is not an incident.
 * Anything that escapes as a plain {@link RuntimeException} is treated as a genuine 500.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION");
    }

    public BusinessException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public BusinessException(String message, HttpStatus status, String code, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
