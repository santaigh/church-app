package com.church.app.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rupee amounts written out, Indian style.
 *
 * <p>Grouping is two-two-three -- thousand, then <b>lakh</b>, then <b>crore</b> -- not the
 * thousand/million/billion of the international system. ₹12,34,567 is "Twelve Lakh Thirty
 * Four Thousand Five Hundred Sixty Seven", and a receipt that said "one point two million"
 * would be read as wrong by everyone holding it.
 *
 * <p>Exposed to templates as {@code ${@amountInWords.of(amount)}}.
 */
@Component("amountInWords")
public class AmountInWords {

    private static final String[] UNITS = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    /**
     * The full line printed on a receipt.
     *
     * <p>Paise are included only when there are any: "Rupees Five Hundred Only" reads
     * better than "Rupees Five Hundred and Zero Paise Only", and a receipt is read aloud
     * more often than it is filed.
     */
    public String of(BigDecimal amount) {
        if (amount == null) {
            return "";
        }

        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP).abs();
        long rupees = value.longValue();
        int paise = value.subtract(BigDecimal.valueOf(rupees))
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        StringBuilder words = new StringBuilder("Rupees ").append(words(rupees));
        if (paise > 0) {
            words.append(" and ").append(words(paise)).append(" Paise");
        }
        return words.append(" Only").toString();
    }

    /** The number alone, without the currency or the closing "Only". */
    public String words(long number) {
        if (number == 0) {
            return "Zero";
        }

        StringBuilder result = new StringBuilder();
        long remaining = number;

        // Two-digit groups above the thousand, which is what makes this Indian rather
        // than international: crore and lakh, not million and billion.
        remaining = appendGroup(result, remaining, 10_000_000L, "Crore");
        remaining = appendGroup(result, remaining, 100_000L, "Lakh");
        remaining = appendGroup(result, remaining, 1_000L, "Thousand");
        remaining = appendGroup(result, remaining, 100L, "Hundred");

        if (remaining > 0) {
            append(result, belowHundred((int) remaining));
        }
        return result.toString();
    }

    private long appendGroup(StringBuilder result, long remaining, long unit, String label) {
        long count = remaining / unit;
        if (count > 0) {
            // Crore can itself run past a hundred -- 100 crore is a real amount -- so it
            // recurses rather than assuming two digits.
            append(result, unit == 10_000_000L ? words(count) : belowHundred((int) count));
            append(result, label);
        }
        return remaining % unit;
    }

    private String belowHundred(int number) {
        if (number < 20) {
            return UNITS[number];
        }
        String tens = TENS[number / 10];
        int unit = number % 10;
        return unit == 0 ? tens : tens + " " + UNITS[unit];
    }

    private void append(StringBuilder result, String word) {
        if (word == null || word.isBlank()) {
            return;
        }
        if (!result.isEmpty()) {
            result.append(' ');
        }
        result.append(word);
    }
}
