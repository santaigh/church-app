package com.church.app.controller;

import com.church.app.exception.BusinessException;
import com.church.app.exception.DuplicateResourceException;
import com.church.app.exception.ResourceNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately throws each exception the {@code GlobalExceptionHandler} handles, so the
 * error plumbing can be exercised without waiting for a real failure.
 *
 * <p>Restricted to the {@code dev} profile -- these endpoints are never registered in
 * production. Delete once the real modules provide better coverage.
 */
@RestController
@RequestMapping("/dev/errors")
@Profile("dev")
@Validated
public class DevErrorTestController {

    @GetMapping("/not-found")
    public String notFound() {
        throw new ResourceNotFoundException("Member", 4242);
    }

    @GetMapping("/duplicate")
    public String duplicate() {
        throw new DuplicateResourceException("Family", "familyCode", "FAM-001");
    }

    @GetMapping("/business")
    public String business() {
        throw new BusinessException("A payment cannot be recorded for a future month.");
    }

    @GetMapping("/runtime")
    public String runtime() {
        throw new IllegalStateException("Simulated unexpected failure");
    }

    /** Triggers ConstraintViolationException: call without {@code name}, or with a 1-char value. */
    @GetMapping("/validation")
    public String validation(@RequestParam @NotBlank @Size(min = 2, message = "must be at least 2 characters") String name) {
        return "valid: " + name;
    }

    /** Triggers MethodArgumentTypeMismatchException when the path segment is not numeric. */
    @GetMapping("/type-mismatch/{id}")
    public String typeMismatch(@PathVariable Long id) {
        return "id: " + id;
    }
}
