package com.church.app.dto;

import com.church.app.entity.ClergyRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * An appointment to a parish.
 *
 * <p>No church field, deliberately: the parish comes from the tenant scope, never from
 * the form.
 */
@Getter
@Setter
public class ParishPriestForm {

    private Long id;

    @NotNull(message = "Choose a role")
    private ClergyRole clergyRole = ClergyRole.PARISH_PRIEST;

    /** Carries the honorific as a parish writes it: {@code Fr. Antony Raj}, {@code Br. Selvam}. */
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name may be at most 100 characters")
    private String priestName;

    @Size(max = 100, message = "Previous place may be at most 100 characters")
    private String priestLastPlace;

    @NotNull(message = "A start date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    /** Empty means currently serving. That is the only definition of "current". */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
}
