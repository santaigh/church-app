package com.church.app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rupees written out for a receipt, in lakh and crore rather than millions. */
class AmountInWordsTests {

    private final AmountInWords words = new AmountInWords();

    @Test
    @DisplayName("everyday parish amounts")
    void ordinaryAmounts() {
        assertEquals("Rupees Fifty Only", words.of(new BigDecimal("50")));
        assertEquals("Rupees Two Hundred Only", words.of(new BigDecimal("200")));
        assertEquals("Rupees One Thousand Five Hundred Only", words.of(new BigDecimal("1500")));
        assertEquals("Rupees Nine Hundred Only", words.of(new BigDecimal("900")));
    }

    @Test
    @DisplayName("grouping is Indian: thousand, lakh, crore")
    void indianGrouping() {
        assertEquals("Rupees One Thousand Only", words.of(new BigDecimal("1000")));
        assertEquals("Rupees One Lakh Only", words.of(new BigDecimal("100000")));
        assertEquals("Rupees One Crore Only", words.of(new BigDecimal("10000000")));

        // The number a receipt would be read wrong on if this used millions.
        assertEquals("Twelve Lakh Thirty Four Thousand Five Hundred Sixty Seven",
                words.words(1234567L));
    }

    @Test
    @DisplayName("paise appear only when there are any")
    void paiseAreOptional() {
        assertEquals("Rupees Five Hundred Only", words.of(new BigDecimal("500.00")));
        assertEquals("Rupees Five Hundred and Fifty Paise Only", words.of(new BigDecimal("500.50")));
        assertEquals("Rupees One and Five Paise Only", words.of(new BigDecimal("1.05")));
    }

    @Test
    @DisplayName("the awkward numbers")
    void edgeCases() {
        assertEquals("Rupees Zero Only", words.of(BigDecimal.ZERO));
        assertEquals("Zero", words.words(0));
        assertEquals("Nineteen", words.words(19));
        assertEquals("Twenty", words.words(20));
        assertEquals("Ninety Nine", words.words(99));
        // No stray "Zero" in the middle of a number.
        assertEquals("One Thousand Five", words.words(1005));
        assertEquals("One Lakh One", words.words(100001));
    }

    @Test
    @DisplayName("crore runs past a hundred rather than breaking")
    void largeCrores() {
        assertEquals("One Hundred Crore", words.words(1_000_000_000L));
        assertEquals("Twelve Crore Thirty Four Lakh Fifty Six Thousand Seven Hundred Eighty Nine",
                words.words(123_456_789L));
    }

    @Test
    @DisplayName("rounding is to the paise, half up")
    void rounding() {
        assertEquals("Rupees One Hundred and One Paise Only", words.of(new BigDecimal("100.005")));
    }
}
