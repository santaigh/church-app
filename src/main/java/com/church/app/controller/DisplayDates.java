package com.church.app.controller;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;

/**
 * One date format for the whole application: {@code dd-MM-yyyy}.
 *
 * <p>Exposed to templates as {@code ${@dates.format(value)}} so every screen reads from
 * the same definition. Left to each page, {@code 2021-06-01} and {@code 01-06-2021} end
 * up side by side, and a reader has to work out which is which.
 *
 * <p>This is display only. {@code <input type="date">} is required by HTML to carry an
 * ISO value, and the browser renders it in the user's own locale -- so forms keep
 * {@code yyyy-MM-dd} in the markup no matter what this returns.
 */
@Component("dates")
public class DisplayDates {

    private static final DateTimeFormatter DAY_FIRST = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** An em dash rather than an empty cell: absent is a fact worth showing. */
    public static final String ABSENT = "—";

    public String format(LocalDate date) {
        return date == null ? ABSENT : date.format(DAY_FIRST);
    }

    public String format(LocalDateTime dateTime) {
        return dateTime == null ? ABSENT : dateTime.toLocalDate().format(DAY_FIRST);
    }

    /**
     * Age in completed years, worked out from the date of birth every time it is asked
     * for.
     *
     * <p>Deliberately not a column. A stored age is wrong the day after it is written,
     * and nothing in a database tells you it has gone stale -- the date of birth is the
     * fact, the age is a view of it.
     *
     * @return the age, or {@link #ABSENT} when no date of birth is recorded
     */
    public String age(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return ABSENT;
        }
        LocalDate today = LocalDate.now();
        if (dateOfBirth.isAfter(today)) {
            // A date of birth in the future is bad data, not a negative age.
            return ABSENT;
        }
        return String.valueOf(Period.between(dateOfBirth, today).getYears());
    }
}
