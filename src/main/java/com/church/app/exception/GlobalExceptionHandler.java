package com.church.app.exception;

import com.church.app.dto.ErrorResponse;
import com.church.app.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single place where unhandled exceptions become responses.
 *
 * <p>Content negotiation is explicit: browser navigations get a rendered Thymeleaf error
 * page, while API/AJAX callers get an {@link ErrorResponse} as JSON. Handlers return
 * {@code Object} so one method can serve both.
 *
 * <p>Logging policy -- 4xx are logged at WARN with the message only, since they are
 * caller mistakes and their stack traces are noise. 5xx are logged at ERROR with the full
 * stack trace and the correlation id, which is also shown to the user so a bug report can
 * be matched to the exact log entry.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Paths browsers fetch on their own; their 404s are not worth a warning. */
    private static final java.util.Set<String> BROWSER_PROBE_PATHS = java.util.Set.of(
            "/favicon.ico", "/robots.txt", "/apple-touch-icon.png",
            "/apple-touch-icon-precomposed.png", "/.well-known/appspecific/com.chrome.devtools.json"
    );

    private static final String GENERIC_500_MESSAGE =
            "Something went wrong while processing your request. Please try again, "
            + "and quote the reference below if the problem continues.";

    // ---------------------------------------------------------------- business failures

    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("{} at {}: {}", ex.getCode(), request.getRequestURI(), ex.getMessage());
        return respond(request, ex.getStatus(), ex.getCode(), ex.getMessage(), null);
    }

    // ---------------------------------------------------------------- validation

    /** Bean Validation failure on an {@code @Valid @RequestBody} or {@code @ModelAttribute}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = extractFieldErrors(ex.getBindingResult().getFieldErrors());
        log.warn("Validation failed at {}: {}", request.getRequestURI(), fieldErrors);
        return respond(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Please correct the highlighted fields.", fieldErrors);
    }

    @ExceptionHandler(BindException.class)
    public Object handleBind(BindException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = extractFieldErrors(ex.getFieldErrors());
        log.warn("Binding failed at {}: {}", request.getRequestURI(), fieldErrors);
        return respond(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Please correct the highlighted fields.", fieldErrors);
    }

    /** Bean Validation failure on {@code @Validated} method parameters (e.g. {@code @RequestParam}). */
    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            // Property paths arrive as "methodName.parameterName"; only the parameter
            // is meaningful to the caller.
            String propertyPath = String.valueOf(v.getPropertyPath());
            int lastDot = propertyPath.lastIndexOf('.');
            String field = lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
            violations.put(field, v.getMessage());
        });
        log.warn("Constraint violation at {}: {}", request.getRequestURI(), violations);
        return respond(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Please correct the highlighted fields.", violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String required = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "the expected type";
        String message = "'%s' is not a valid value for %s (expected %s)."
                .formatted(ex.getValue(), ex.getName(), required);
        log.warn("Type mismatch at {}: {}", request.getRequestURI(), message);
        return respond(request, HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", message, null);
    }

    // ---------------------------------------------------------------- routing

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Object handleNotFound(Exception ex, HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Browsers request these unprompted on every page view; a WARN each time would
        // bury real misses.
        if (BROWSER_PROBE_PATHS.contains(uri)) {
            log.debug("Ignoring browser probe for {}", uri);
        } else {
            log.warn("No handler for {} {}", request.getMethod(), uri);
        }
        return respond(request, HttpStatus.NOT_FOUND, "NOT_FOUND",
                "The page you requested does not exist.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method {} not supported at {}", ex.getMethod(), request.getRequestURI());
        return respond(request, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "%s is not supported for this address.".formatted(ex.getMethod()), null);
    }

    // ---------------------------------------------------------------- persistence

    /**
     * A unique/foreign-key breach that slipped past service-level checks. The driver
     * message is logged but never shown -- it exposes table and constraint names.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation at {}", request.getRequestURI(), ex);
        return respond(request, HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "This change conflicts with existing data. It may already exist, "
                + "or another record may depend on it.", null);
    }

    // ---------------------------------------------------------------- catch-all

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", GENERIC_500_MESSAGE, null);
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, String> extractFieldErrors(java.util.List<FieldError> errors) {
        Map<String, String> map = new LinkedHashMap<>();
        for (FieldError error : errors) {
            // Keep the first message per field; later ones are usually less specific.
            map.putIfAbsent(error.getField(),
                    error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value");
        }
        return map;
    }

    private Object respond(HttpServletRequest request, HttpStatus status, String code,
                           String message, Map<String, String> fieldErrors) {
        String correlationId = correlationIdOf(request);
        String path = request.getRequestURI();

        if (prefersJson(request)) {
            ErrorResponse body = fieldErrors == null
                    ? ErrorResponse.of(status.value(), status.getReasonPhrase(), code, message, path, correlationId)
                    : ErrorResponse.withFieldErrors(status.value(), status.getReasonPhrase(), code, message,
                            path, correlationId, fieldErrors);
            return ResponseEntity.status(status).body(body);
        }

        ModelAndView mav = new ModelAndView(viewNameFor(status));
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("error", status.getReasonPhrase());
        mav.addObject("code", code);
        mav.addObject("message", message);
        mav.addObject("path", path);
        mav.addObject("correlationId", correlationId);
        mav.addObject("fieldErrors", fieldErrors);
        return mav;
    }

    /** Dedicated templates exist for the common statuses; everything else shares error/error.html. */
    private String viewNameFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "error/404";
            case FORBIDDEN -> "error/403";
            case INTERNAL_SERVER_ERROR -> "error/500";
            default -> "error/error";
        };
    }

    private String correlationIdOf(HttpServletRequest request) {
        Object id = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return id != null ? id.toString() : "n/a";
    }

    /**
     * True for API paths, explicit JSON Accept headers, and XHR calls -- i.e. anything
     * that would choke on an HTML page. A browser navigation sends {@code text/html} and
     * falls through to the rendered view.
     */
    private boolean prefersJson(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept == null) {
            return false;
        }
        return accept.contains(MediaType.APPLICATION_JSON_VALUE) && !accept.contains(MediaType.TEXT_HTML_VALUE);
    }
}
