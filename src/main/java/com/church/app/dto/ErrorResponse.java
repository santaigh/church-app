package com.church.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Error payload returned to API/AJAX callers. Null members are omitted from the JSON,
 * so a response without field-level problems carries no empty {@code fieldErrors} object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(int status, String error, String code, String message,
                                   String path, String correlationId) {
        return new ErrorResponse(LocalDateTime.now(), status, error, code, message, path, correlationId, null);
    }

    public static ErrorResponse withFieldErrors(int status, String error, String code, String message,
                                                String path, String correlationId,
                                                Map<String, String> fieldErrors) {
        return new ErrorResponse(LocalDateTime.now(), status, error, code, message, path, correlationId, fieldErrors);
    }
}
