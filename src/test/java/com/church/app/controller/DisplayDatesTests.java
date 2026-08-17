package com.church.app.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one place dates and ages are turned into text.
 *
 * <p>No Spring context: this is arithmetic and formatting, and it should be provable
 * without starting an application.
 */
class DisplayDatesTests {

    private final DisplayDates dates = new DisplayDates();

    @Test
    @DisplayName("dates read day first")
    void datesAreDayFirst() {
        assertEquals("01-06-2021", dates.format(LocalDate.of(2021, 6, 1)));
        assertEquals("31-12-1999", dates.format(LocalDate.of(1999, 12, 31)));
    }

    @Test
    @DisplayName("a missing date is marked absent rather than left blank")
    void missingDatesAreMarked() {
        assertEquals(DisplayDates.ABSENT, dates.format((LocalDate) null));
        assertEquals(DisplayDates.ABSENT, dates.age(null));
    }

    @Test
    @DisplayName("age counts completed years, so a birthday not yet reached does not count")
    void ageCountsCompletedYears() {
        LocalDate today = LocalDate.now();

        assertEquals("30", dates.age(today.minusYears(30)));
        // One day short of the birthday is still the year before.
        assertEquals("29", dates.age(today.minusYears(30).plusDays(1)));
        assertEquals("30", dates.age(today.minusYears(30).minusDays(1)));
    }

    @Test
    @DisplayName("a birth date in the future is bad data, not a negative age")
    void futureBirthDatesAreRefused() {
        assertEquals(DisplayDates.ABSENT, dates.age(LocalDate.now().plusDays(1)));
    }

    @Test
    @DisplayName("a child born today is nought, not blank")
    void newbornIsZero() {
        assertEquals("0", dates.age(LocalDate.now()));
    }
}
